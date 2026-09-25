package com.manarah.video;

import com.manarah.course.repo.LessonMaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Turns uploaded lesson videos into HLS: 360p and 720p (and 1080p when the source has it) in six-second segments,
 * each encrypted with AES-128 under a key only a live watch session receives — so the player switches quality with
 * the connection, and a segment copied out of the network tab is useless on its own. One video at a time; the
 * original stays, and a video that fails keeps playing as it was uploaded.
 *
 * <p>Off unless {@code manarah.video.transcode.enabled} is on: FFmpeg needs real CPU, which the server has to have.
 */
@Component
@ConditionalOnProperty(name = "manarah.video.transcode.enabled", havingValue = "true")
public class VideoTranscodeJob {
    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeJob.class);
    private static final long LOCK_KEY = 851205L;
    /** Height and video bitrate of each quality offered. */
    private static final int[][] LADDER = {{360, 800}, {720, 2500}, {1080, 5000}};

    private final VideoAssetRepository assets;
    private final VideoStore store;
    private final LessonMaterialRepository materials;
    private final JdbcTemplate jdbc;
    private final String ffmpeg, ffprobe;
    private final Path workDir;

    public VideoTranscodeJob(VideoAssetRepository assets, VideoStore store, LessonMaterialRepository materials, JdbcTemplate jdbc,
                             @Value("${manarah.video.transcode.ffmpeg:ffmpeg}") String ffmpeg,
                             @Value("${manarah.video.transcode.ffprobe:ffprobe}") String ffprobe,
                             @Value("${manarah.video.transcode.work-dir:${java.io.tmpdir}}") String workDir) {
        this.assets = assets; this.store = store; this.materials = materials; this.jdbc = jdbc;
        this.ffmpeg = ffmpeg; this.ffprobe = ffprobe; this.workDir = Path.of(workDir);
    }

    @Scheduled(fixedDelayString = "${manarah.video.transcode.interval-ms:30000}", initialDelay = 45000)
    public void run() {
        boolean locked;
        try { locked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, LOCK_KEY)); }
        catch (DataAccessException e) { locked = true; }
        if (!locked) return;
        try { assets.findFirstByStatusOrderByIdAsc(VideoAsset.QUEUED).ifPresent(this::process); }
        finally {
            try { jdbc.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, LOCK_KEY); } catch (DataAccessException ignored) {}
        }
    }

    void process(VideoAsset a) {
        a.setStatus(VideoAsset.PROCESSING);
        assets.save(a);
        Path dir = null;
        try {
            Files.createDirectories(workDir);
            dir = Files.createTempDirectory(workDir, "video-" + a.getId() + "-");
            Path source = dir.resolve("source");
            store.download(a.getObjectKey(), source);
            int height = probeInt(source, "v:0", "stream=height");
            boolean audio = !probe(source, "a:0", "stream=codec_type").isBlank();
            int duration = (int) Math.round(Double.parseDouble(probe(source, null, "format=duration").trim()));

            List<int[]> ladder = new ArrayList<>();
            for (int[] q : LADDER) if (q[0] <= Math.max(height, 360)) ladder.add(q);

            byte[] key = new byte[16];
            new SecureRandom().nextBytes(key);
            Path keyFile = dir.resolve("enc.key");
            Files.write(keyFile, key);
            Path keyInfo = dir.resolve("enc.keyinfo");
            // Line 1 is the key's URI in the playlist (we rewrite it per viewer), line 2 where ffmpeg reads it.
            Files.writeString(keyInfo, "key\n" + keyFile.toAbsolutePath() + "\n", StandardCharsets.UTF_8);

            Path out = dir.resolve("hls");
            Files.createDirectories(out);
            List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", source.toString()));
            StringBuilder filter = new StringBuilder("[0:v]split=" + ladder.size());
            for (int i = 0; i < ladder.size(); i++) filter.append("[s").append(i).append("]");
            for (int i = 0; i < ladder.size(); i++) filter.append(";[s").append(i).append("]scale=-2:").append(ladder.get(i)[0]).append("[o").append(i).append("]");
            cmd.addAll(List.of("-filter_complex", filter.toString()));
            StringBuilder streams = new StringBuilder();
            for (int i = 0; i < ladder.size(); i++) {
                int kbps = ladder.get(i)[1];
                cmd.addAll(List.of("-map", "[o" + i + "]", "-c:v:" + i, "libx264", "-b:v:" + i, kbps + "k",
                        "-maxrate:v:" + i, (kbps * 107 / 100) + "k", "-bufsize:v:" + i, (kbps * 3 / 2) + "k"));
                if (audio) cmd.addAll(List.of("-map", "a:0", "-c:a:" + i, "aac", "-b:a:" + i, "128k", "-ac", "2"));
                streams.append(i == 0 ? "" : " ").append("v:").append(i).append(audio ? ",a:" + i : "");
            }
            cmd.addAll(List.of("-preset", "veryfast", "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                    "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod", "-hls_key_info_file", keyInfo.toString(),
                    "-hls_segment_filename", out.resolve("v%v").resolve("seg_%03d.ts").toString(),
                    "-master_pl_name", "master.m3u8", "-var_stream_map", streams.toString(), out.resolve("v%v").resolve("index.m3u8").toString()));
            runOrFail(cmd, dir.resolve("ffmpeg.log"), TimeUnit.HOURS.toSeconds(6));

            String prefix = a.getObjectKey().substring(0, a.getObjectKey().lastIndexOf('/')) + "/hls";
            try (Stream<Path> files = Files.walk(out)) {
                for (Path f : files.filter(Files::isRegularFile).toList()) {
                    String rel = out.relativize(f).toString().replace('\\', '/');
                    store.put(prefix + "/" + rel, f, rel.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t");
                }
            }
            a.setHlsPrefix(prefix);
            a.setHlsKey(HexFormat.of().formatHex(key));
            a.setDurationSec(duration);
            a.setStatus(VideoAsset.STREAMING);
            a.setProcessedAt(Instant.now());
            a.setError("");
            assets.save(a);
            materials.findByVideoAssetId(a.getId()).forEach(m -> { m.setDurationSec(duration); materials.save(m); });
            log.info("video_transcode outcome=done asset={} renditions={} seconds={}", a.getId(), ladder.size(), duration);
        } catch (Exception e) {
            // It still plays as uploaded; the error says why it isn't streaming.
            a.setStatus(VideoAsset.READY);
            a.setError(Objects.toString(e.getMessage(), e.toString()).substring(0, Math.min(500, Objects.toString(e.getMessage(), e.toString()).length())));
            assets.save(a);
            log.warn("video_transcode outcome=failed asset={} cause={}", a.getId(), e.toString());
        } finally {
            if (dir != null) deleteTree(dir);
        }
    }

    private int probeInt(Path source, String stream, String entry) throws IOException, InterruptedException {
        String v = probe(source, stream, entry).trim();
        return v.isEmpty() ? 0 : Integer.parseInt(v.split("\\s+")[0]);
    }

    private String probe(Path source, String stream, String entry) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(ffprobe, "-v", "error"));
        if (stream != null) cmd.addAll(List.of("-select_streams", stream));
        cmd.addAll(List.of("-show_entries", entry, "-of", "default=noprint_wrappers=1:nokey=1", source.toString()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(2, TimeUnit.MINUTES)) { p.destroyForcibly(); throw new IOException("ffprobe timed out"); }
        return p.exitValue() == 0 ? outText : "";
    }

    private static void runOrFail(List<String> cmd, Path logFile, long timeoutSeconds) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("ffmpeg timed out"); }
        if (p.exitValue() != 0) {
            String tail = Files.exists(logFile) ? Files.readString(logFile) : "";
            throw new IOException("ffmpeg failed: " + tail.substring(Math.max(0, tail.length() - 400)));
        }
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
