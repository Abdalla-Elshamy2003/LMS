package com.manarah.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.exam.ExamDtos.CreateQuestionRequest;
import com.manarah.exam.ExamDtos.OptionInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-assisted question-bank drafting via any OpenAI-compatible chat-completions endpoint
 * (configured for OpenRouter by default). A teacher gives a subject/topic/difficulty and gets
 * back ready-to-review question drafts — nothing is written to the bank here; the caller
 * ({@link ExamService#generateQuestions}) hands the drafts back to the frontend for edit/select,
 * and each accepted draft is saved through the existing {@code POST /exams/questions} endpoint.
 *
 * <p>To enable, set (env vars work too — Spring relaxed binding maps
 * {@code MANARAH_AI_API_KEY} etc.):
 * <pre>
 *   manarah.ai.enabled=true
 *   manarah.ai.api-key=&lt;an OpenRouter (or other OpenAI-compatible) API key&gt;
 * </pre>
 * Until those are set, {@link #generate} throws a clear {@link BadRequestException} rather than
 * silently returning nothing.
 */
@Component
public class AiQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiQuestionGenerator.class);
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestClient restClient = com.manarah.common.ai.AiHttp.client();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Files at or below this ride inline in the request; larger ones go through the Files API. */
    private final int inlineLimitBytes;

    public AiQuestionGenerator(
            @Value("${manarah.ai.enabled:false}") boolean enabled,
            @Value("${manarah.ai.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${manarah.ai.api-key:}") String apiKey,
            @Value("${manarah.ai.model:inclusionai/ling-3.0-flash-fin:free}") String model,
            @Value("${manarah.ai.inline-limit-bytes:15728640}") int inlineLimitBytes) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.inlineLimitBytes = inlineLimitBytes;
    }

    private boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /** Draft question shape returned by the model, before it's mapped onto {@link CreateQuestionRequest}. */
    private record DraftOption(String text, boolean correct) {
    }

    private record Draft(String stem, List<DraftOption> options, String correctAnswer) {
    }

    public List<CreateQuestionRequest> generate(String subject, String topic, String difficulty, String type, int count) {
        if (!isConfigured()) {
            throw new BadRequestException("توليد الأسئلة بالذكاء الاصطناعي غير مُفعّل — يحتاج إعداد مفتاح API أولاً");
        }
        int n = Math.max(1, Math.min(count, 10));
        String diff = (difficulty == null || difficulty.isBlank()) ? "MEDIUM" : difficulty.toUpperCase();
        String qType = (type == null || type.isBlank()) ? "MCQ" : type.toUpperCase();

        String prompt = buildPrompt(subject, topic, diff, qType, n);
        String content;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.7);

            String response = restClient.post()
                    .uri(baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions")
                    .headers(h -> {
                        h.setBearerAuth(apiKey);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);

            var root = mapper.readTree(response == null ? "{}" : response);
            var choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("[AI] no choices in response: {}", response);
                throw new BadRequestException("لم يتمكن النموذج من توليد أسئلة، حاول مرة أخرى");
            }
            content = choices.get(0).path("message").path("content").asText("");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] question generation failed: {}", e.getMessage());
            throw new BadRequestException("تعذّر الاتصال بخدمة الذكاء الاصطناعي، حاول مرة أخرى");
        }

        List<Draft> drafts = parseDrafts(content);
        if (drafts.isEmpty()) {
            throw new BadRequestException("لم يتمكن النموذج من توليد أسئلة صالحة، حاول مرة أخرى بصياغة مختلفة");
        }

        return drafts.stream().map(d -> new CreateQuestionRequest(
                subject, null, null, diff, qType, d.stem(), 1.0, d.correctAnswer(), null, topic,
                d.options() == null ? null : d.options().stream()
                        .map(o -> new OptionInput(o.text(), o.correct(), null)).toList()
        )).toList();
    }

    /**
     * Same drafts, but grounded in an uploaded PDF or page photo (textbook chapter, notes, past
     * paper). Sent through Gemini's native multimodal endpoint, which reads Arabic PDFs and scanned
     * pages directly — no text extraction step to mangle the script.
     */
    public List<CreateQuestionRequest> generateFromFile(org.springframework.web.multipart.MultipartFile file, String subject,
                                                        String focus, String difficulty, String type, int count) {
        if (!isConfigured()) {
            throw new BadRequestException("توليد الأسئلة بالذكاء الاصطناعي غير مُفعّل — يحتاج إعداد مفتاح API أولاً");
        }
        if (!baseUrl.contains("generativelanguage.googleapis.com"))
            throw new BadRequestException("توليد الأسئلة من ملف متاح مع Gemini فقط");
        if (file == null || file.isEmpty()) throw new BadRequestException("ارفع ملف PDF أو صورة");
        if (file.getSize() > MAX_FILE_BYTES) throw new BadRequestException("الملف أكبر من 120 ميجابايت — قسّمه لأجزاء أصغر");
        String name = java.util.Objects.toString(file.getOriginalFilename(), "").toLowerCase(java.util.Locale.ROOT);
        String mime = name.endsWith(".pdf") ? "application/pdf" : name.endsWith(".png") ? "image/png"
                : name.endsWith(".webp") ? "image/webp" : (name.endsWith(".jpg") || name.endsWith(".jpeg")) ? "image/jpeg" : null;
        if (mime == null) throw new BadRequestException("الملف يجب أن يكون PDF أو صورة (PNG / JPG / WEBP)");

        int n = Math.max(1, Math.min(count, 20));
        String diff = (difficulty == null || difficulty.isBlank()) ? "MEDIUM" : difficulty.toUpperCase();
        String qType = (type == null || type.isBlank()) ? "MCQ" : type.toUpperCase();
        String prompt = buildPrompt(subject, focus, diff, qType, n)
                + "\nمهم جداً: اعتمد **فقط** على محتوى الملف المرفق (كتاب/مذكرة/ملزمة/ورقة امتحان) في صياغة الأسئلة، "
                + "ولا تضف معلومات من خارجه. غطِّ أهم النقاط الواردة فيه بالترتيب.";

        String content;
        String uploadedName = null;
        String nativeBase = baseUrl.replaceAll("/openai/?$", "");
        try {
            byte[] bytes = file.getBytes();
            // Small files ride inside the request; anything bigger goes through the Files API first,
            // which takes PDFs up to 2 GB / 1000 pages and hands back a URI the model reads from.
            Map<String, Object> filePart;
            if (bytes.length <= inlineLimitBytes) {
                filePart = Map.of("inline_data", Map.of("mime_type", mime, "data", java.util.Base64.getEncoder().encodeToString(bytes)));
            } else {
                var uploaded = uploadToGemini(nativeBase, bytes, mime, name);
                uploadedName = uploaded.name();
                filePart = Map.of("file_data", Map.of("mime_type", mime, "file_uri", uploaded.uri()));
            }
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt), filePart))),
                    "generationConfig", Map.of("temperature", 0.6));
            String modelId = model.startsWith("models/") ? model : "models/" + model;
            String response = null;
            // Gemini answers 503 for a few seconds under load; one short retry rides that out.
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    response = restClient.post()
                            .uri(nativeBase + "/" + modelId + ":generateContent")
                            .headers(h -> { h.set("x-goog-api-key", apiKey); h.setContentType(MediaType.APPLICATION_JSON); })
                            .body(body)
                            .retrieve()
                            .body(String.class);
                    break;
                } catch (org.springframework.web.client.HttpServerErrorException e) {
                    if (attempt == 3) throw e;
                    Thread.sleep(2500L * attempt);
                }
            }
            var candidates = mapper.readTree(response == null ? "{}" : response).path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("[AI] no candidates in file response: {}", response);
                throw new BadRequestException("لم يتمكن النموذج من قراءة الملف، جرّب ملفاً أوضح");
            }
            StringBuilder sb = new StringBuilder();
            for (var part : candidates.get(0).path("content").path("parts")) sb.append(part.path("text").asText(""));
            content = sb.toString();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] file question generation failed: {}", e.getMessage());
            throw new BadRequestException("تعذّر الاتصال بخدمة الذكاء الاصطناعي، حاول مرة أخرى");
        } finally {
            if (uploadedName != null) deleteFromGemini(nativeBase, uploadedName);
        }

        List<Draft> drafts = parseDrafts(content);
        if (drafts.isEmpty()) {
            throw new BadRequestException("لم يتمكن النموذج من توليد أسئلة صالحة من هذا الملف، جرّب ملفاً آخر أو عدداً أقل");
        }
        String tag = focus == null || focus.isBlank() ? "من ملف" : focus;
        return drafts.stream().map(d -> new CreateQuestionRequest(
                subject, null, null, diff, qType, d.stem(), 1.0, d.correctAnswer(), null, tag,
                d.options() == null ? null : d.options().stream()
                        .map(o -> new OptionInput(o.text(), o.correct(), null)).toList()
        )).toList();
    }

    private static final long MAX_FILE_BYTES = 120L * 1024 * 1024;

    private record UploadedFile(String name, String uri) {}

    /** Resumable upload to Gemini's Files API, then wait until the file is processed (PDFs take a few seconds). */
    private UploadedFile uploadToGemini(String nativeBase, byte[] bytes, String mime, String displayName) throws Exception {
        String uploadBase = nativeBase.replace("/v1beta", "").replace("generativelanguage.googleapis.com", "generativelanguage.googleapis.com/upload") + "/v1beta/files";
        var start = restClient.post()
                .uri(uploadBase)
                .headers(h -> {
                    h.set("x-goog-api-key", apiKey);
                    h.set("X-Goog-Upload-Protocol", "resumable");
                    h.set("X-Goog-Upload-Command", "start");
                    h.set("X-Goog-Upload-Header-Content-Length", String.valueOf(bytes.length));
                    h.set("X-Goog-Upload-Header-Content-Type", mime);
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(Map.of("file", Map.of("display_name", displayName.isBlank() ? "upload" : displayName)))
                .retrieve()
                .toEntity(String.class);
        String uploadUrl = start.getHeaders().getFirst("X-Goog-Upload-URL");
        if (uploadUrl == null) throw new IllegalStateException("no upload url from Files API");

        String finalized = restClient.post()
                .uri(uploadUrl)
                .headers(h -> {
                    h.set("X-Goog-Upload-Offset", "0");
                    h.set("X-Goog-Upload-Command", "upload, finalize");
                    h.setContentType(MediaType.parseMediaType(mime));
                })
                .body(bytes)
                .retrieve()
                .body(String.class);
        var fileNode = mapper.readTree(finalized == null ? "{}" : finalized).path("file");
        String fileName = fileNode.path("name").asText(null);
        String uri = fileNode.path("uri").asText(null);
        if (fileName == null || uri == null) throw new IllegalStateException("Files API returned no file: " + finalized);

        // Poll until ACTIVE — large PDFs sit in PROCESSING for a while.
        String state = fileNode.path("state").asText("PROCESSING");
        for (int i = 0; i < 40 && "PROCESSING".equals(state); i++) {
            Thread.sleep(2000);
            String info = restClient.get()
                    .uri(nativeBase + "/" + fileName)
                    .headers(h -> h.set("x-goog-api-key", apiKey))
                    .retrieve()
                    .body(String.class);
            state = mapper.readTree(info == null ? "{}" : info).path("state").asText("PROCESSING");
        }
        if (!"ACTIVE".equals(state)) throw new BadRequestException("الملف كبير جداً أو تعذّرت معالجته — جرّب جزءاً أصغر منه");
        return new UploadedFile(fileName, uri);
    }

    /** Best effort: Gemini deletes uploads after 48h anyway. */
    private void deleteFromGemini(String nativeBase, String fileName) {
        try {
            restClient.delete().uri(nativeBase + "/" + fileName).headers(h -> h.set("x-goog-api-key", apiKey)).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.debug("[AI] could not delete uploaded file {}: {}", fileName, e.getMessage());
        }
    }

    private String buildPrompt(String subject, String topic, String difficulty, String type, int count) {
        String typeGuide = "TRUE_FALSE".equals(type)
                ? "كل سؤال يجب أن يحتوي على options بالضبط عنصرين: {\"text\":\"صح\",\"correct\":true|false} و {\"text\":\"خطأ\",\"correct\":true|false} (عنصر واحد فقط صحيح)."
                : "كل سؤال يجب أن يحتوي على 4 اختيارات (options) بالضبط، اختيار واحد منها صحيح (correct=true) والباقي خاطئة.";
        return """
                أنت مساعد تعليمي متخصص في صياغة أسئلة امتحانات لطلاب المدارس والمراكز التعليمية في مصر.
                المطلوب: توليد %d سؤال/أسئلة اختبار باللغة العربية الفصحى، في مادة "%s"%s، بمستوى صعوبة "%s".
                %s
                أرجع النتيجة **فقط** كمصفوفة JSON صالحة بدون أي نص إضافي قبلها أو بعدها، بالشكل التالي بالضبط:
                [
                  {"stem": "نص السؤال", "options": [{"text": "اختيار 1", "correct": false}, {"text": "اختيار 2", "correct": true}], "correctAnswer": null}
                ]
                لا تكتب أي شرح أو مقدمة، فقط مصفوفة JSON.
                """.formatted(count, subject == null || subject.isBlank() ? "عام" : subject,
                topic == null || topic.isBlank() ? "" : (" حول موضوع \"" + topic + "\""), difficulty, typeGuide);
    }

    private List<Draft> parseDrafts(String content) {
        if (content == null || content.isBlank()) return List.of();
        String json = content.trim();
        Matcher m = CODE_FENCE.matcher(json);
        if (m.find()) {
            json = m.group(1).trim();
        }
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end < start) return List.of();
        json = json.substring(start, end + 1);
        try {
            Draft[] arr = mapper.readValue(json, Draft[].class);
            return List.of(arr).stream()
                    .filter(d -> d.stem() != null && !d.stem().isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("[AI] failed to parse model output as JSON: {}", e.getMessage());
            return List.of();
        }
    }
}
