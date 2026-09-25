package com.manarah.video;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.LearningService;
import com.manarah.course.domain.LessonMaterial;
import com.manarah.course.repo.LessonMaterialRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Uploading a lesson video straight from the teacher's browser to the video store, in parts: the backend checks the
 * teacher may add to the lesson, names the file itself (never trusting the browser with a path), hands out one upload
 * URL per part, and — when the browser reports the parts done — completes the upload, checks the stored size matches
 * what was announced, and adds the video to the lesson. The bytes never pass through here when the store is R2.
 */
@Service
public class VideoUploadService {
    /** 16 MB parts: well above R2's 5 MB minimum, and a failed part costs little to resend. */
    public static final long PART_SIZE = 16L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "mp4", "video/mp4", "m4v", "video/mp4", "mov", "video/quicktime", "webm", "video/webm", "mkv", "video/x-matroska");

    private final VideoAssetRepository assets;
    private final VideoStore store;
    private final LearningService learning;
    private final LessonMaterialRepository materials;
    private final com.manarah.audit.AuditService audit;
    private final long maxBytes;
    private final boolean transcode;

    public VideoUploadService(VideoAssetRepository assets, VideoStore store, LearningService learning, LessonMaterialRepository materials,
                              com.manarah.audit.AuditService audit,
                              @Value("${manarah.video.max-bytes:4294967296}") long maxBytes,
                              @Value("${manarah.video.transcode.enabled:false}") boolean transcode) {
        this.assets = assets; this.store = store; this.learning = learning; this.materials = materials; this.audit = audit;
        this.maxBytes = maxBytes; this.transcode = transcode;
    }

    public record StartRequest(Long lessonId, String title, String fileName, Long sizeBytes) {}
    public record StartResult(Long assetId, long partSize, int parts, List<String> urls, boolean withSession) {}
    public record CompleteRequest(List<VideoStore.Part> parts) {}
    public record Completed(Long materialId, Long assetId, String status) {}

    @Transactional
    public StartResult start(UserPrincipal actor, StartRequest req) {
        if (req.lessonId() == null) throw new BadRequestException("اختار الدرس");
        learning.access(actor, learning.lessonCourse(actor, req.lessonId()), true);
        String fileName = Objects.toString(req.fileName(), "").trim();
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!TYPES.containsKey(ext)) throw new BadRequestException("الفيديو لازم يكون MP4 أو MOV أو WEBM أو MKV");
        long size = req.sizeBytes() == null ? 0 : req.sizeBytes();
        if (size <= 0) throw new BadRequestException("الملف فاضي");
        if (size > maxBytes) throw new BadRequestException("الفيديو أكبر من المسموح (" + (maxBytes / (1024 * 1024 * 1024)) + " جيجا)");
        String title = Objects.toString(req.title(), "").trim();
        if (title.length() > 200) throw new BadRequestException("عنوان الفيديو طويل جداً");

        VideoAsset a = new VideoAsset();
        a.setTenantId(actor.getTenantId());
        a.setLessonId(req.lessonId());
        a.setUploadedBy(actor.getId());
        a.setTitle(title.isEmpty() ? fileName.substring(0, dot) : title);
        a.setStorage(store.name());
        a.setObjectKey("t" + actor.getTenantId() + "/videos/" + UUID.randomUUID() + "/original." + ext);
        a.setFileName(fileName.length() > 200 ? fileName.substring(0, 200) : fileName);
        a.setContentType(TYPES.get(ext));
        a.setSizeBytes(size);
        a.setPartSize(PART_SIZE);
        a.setParts((int) ((size + PART_SIZE - 1) / PART_SIZE));
        a.setUploadId(store.startUpload(a.getObjectKey(), a.getContentType()));
        assets.save(a);
        List<String> urls = IntStream.rangeClosed(1, a.getParts()).mapToObj(n -> store.partUrl(a, n)).toList();
        return new StartResult(a.getId(), PART_SIZE, a.getParts(), urls, store.partsNeedSession());
    }

    /** One part sent to our own endpoint (the local store). Returns the part's ETag. No transaction: the body can take a while. */
    public String writePart(UserPrincipal actor, Long assetId, int partNumber, InputStream body) throws IOException {
        if (!(store instanceof LocalVideoStore local)) throw new BadRequestException("الأجزاء بتترفع على التخزين مباشرة");
        VideoAsset a = uploading(actor, assetId);
        if (partNumber < 1 || partNumber > a.getParts()) throw new BadRequestException("رقم جزء غير صالح");
        return local.writePart(a, partNumber, body, PART_SIZE);
    }

    /** A refused upload is recorded as such: the refusal must not roll back marking it aborted (nothing else is written first). */
    @Transactional(noRollbackFor = BadRequestException.class)
    public Completed complete(UserPrincipal actor, Long assetId, CompleteRequest req) {
        VideoAsset a = uploading(actor, assetId);
        List<VideoStore.Part> parts = req == null || req.parts() == null ? List.of() : req.parts();
        Set<Integer> numbers = new HashSet<>();
        for (VideoStore.Part p : parts) {
            if (p.partNumber() < 1 || p.partNumber() > a.getParts() || p.etag() == null || p.etag().isBlank())
                throw new BadRequestException("بيانات الأجزاء ناقصة");
            numbers.add(p.partNumber());
        }
        if (numbers.size() != a.getParts()) throw new BadRequestException("في أجزاء من الفيديو ما اترفعتش لسه");
        store.completeUpload(a, parts.stream().sorted(Comparator.comparingInt(VideoStore.Part::partNumber)).toList());
        long stored = store.size(a.getObjectKey());
        if (stored != a.getSizeBytes()) {
            // What arrived isn't what was announced: don't keep a half file behind a lesson.
            store.delete(a.getObjectKey());
            a.setStatus(VideoAsset.ABORTED); a.setError("size " + stored + " != " + a.getSizeBytes());
            assets.save(a);
            throw new BadRequestException("الفيديو ما اترفعش كامل، جرّب تاني");
        }
        a.setUploadId(null);
        a.setStatus(transcode ? VideoAsset.QUEUED : VideoAsset.READY);
        a.setCompletedAt(Instant.now());
        assets.save(a);

        LessonMaterial m = new LessonMaterial();
        m.setTenantId(a.getTenantId());
        m.setLessonId(a.getLessonId());
        m.setType("VIDEO");
        m.setTitle(a.getTitle());
        m.setSizeBytes(a.getSizeBytes());
        m.setVideoAssetId(a.getId());
        materials.save(m);
        audit.record(actor, "VIDEO_UPLOADED", "VideoAsset", a.getId(), null, a.getStorage() + " " + a.getSizeBytes() + " bytes");
        return new Completed(m.getId(), a.getId(), a.getStatus());
    }

    @Transactional
    public void abort(UserPrincipal actor, Long assetId) {
        VideoAsset a = uploading(actor, assetId);
        store.abortUpload(a);
        a.setStatus(VideoAsset.ABORTED);
        a.setUploadId(null);
        assets.save(a);
    }

    /** An upload still running, started by this person (or one who may edit the lesson) in this space. */
    private VideoAsset uploading(UserPrincipal actor, Long assetId) {
        VideoAsset a = assets.findById(assetId).filter(x -> x.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("الرفع", assetId));
        if (!a.getUploadedBy().equals(actor.getId())) learning.access(actor, learning.lessonCourse(actor, a.getLessonId()), true);
        if (!VideoAsset.UPLOADING.equals(a.getStatus())) throw new ConflictException("الرفع ده خلص أو اتلغى");
        return a;
    }
}
