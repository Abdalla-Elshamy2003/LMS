package com.manarah.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.course.domain.Lesson;
import com.manarah.course.domain.LessonMaterial;
import com.manarah.course.repo.LessonMaterialRepository;
import com.manarah.course.repo.LessonRepository;
import com.manarah.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a student-facing recap of a lesson with the configured chat model (same `manarah.ai.*`
 * settings as question generation). The teacher triggers it, can feed in their own notes or a
 * transcript, and the result is stored on the lesson so students see it under the video.
 */
@Service
public class LessonSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LessonSummaryService.class);
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    public record SummaryRequest(String notes) {}
    public record SummaryView(Long lessonId, String aiSummary) {}
    private record Draft(String summary, List<String> keyPoints) {}

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestClient restClient = com.manarah.common.ai.AiHttp.client();
    private final ObjectMapper mapper = new ObjectMapper();
    private final LessonRepository lessons;
    private final LessonMaterialRepository materials;
    private final LearningService learning;

    public LessonSummaryService(
            @Value("${manarah.ai.enabled:false}") boolean enabled,
            @Value("${manarah.ai.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${manarah.ai.api-key:}") String apiKey,
            @Value("${manarah.ai.model:inclusionai/ling-3.0-flash-fin:free}") String model,
            LessonRepository lessons, LessonMaterialRepository materials, LearningService learning) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.lessons = lessons;
        this.materials = materials;
        this.learning = learning;
    }

    @Transactional
    public SummaryView summarize(UserPrincipal actor, Long lessonId, SummaryRequest req) {
        if (!(enabled && apiKey != null && !apiKey.isBlank()))
            throw new BadRequestException("تلخيص الدروس بالذكاء الاصطناعي غير مُفعّل — يحتاج إعداد مفتاح API أولاً");
        Lesson lesson = lessons.findById(lessonId).filter(l -> l.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("الدرس", lessonId));
        learning.access(actor, learning.lessonCourse(actor, lessonId), true);

        String notes = req == null || req.notes() == null ? "" : req.notes().trim();
        if (notes.length() > 12000) throw new BadRequestException("الملاحظات أطول من الحد المسموح (12,000 حرف)");
        String prompt = buildPrompt(lesson, materials.findByTenantIdAndLessonId(actor.getTenantId(), lessonId), notes);

        String content;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.4);
            String response = restClient.post()
                    .uri(baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions")
                    .headers(h -> { h.setBearerAuth(apiKey); h.setContentType(MediaType.APPLICATION_JSON); })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            var choices = mapper.readTree(response == null ? "{}" : response).path("choices");
            if (!choices.isArray() || choices.isEmpty())
                throw new BadRequestException("لم يتمكن النموذج من تلخيص الدرس، حاول مرة أخرى");
            content = choices.get(0).path("message").path("content").asText("");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] lesson summary failed: {}", e.getMessage());
            throw new BadRequestException("تعذّر الاتصال بخدمة الذكاء الاصطناعي، حاول مرة أخرى");
        }

        Draft draft = parse(content);
        if (draft == null || draft.summary() == null || draft.summary().isBlank())
            throw new BadRequestException("لم يتمكن النموذج من إنتاج ملخص صالح، حاول مرة أخرى");

        StringBuilder text = new StringBuilder(draft.summary().trim());
        if (draft.keyPoints() != null && !draft.keyPoints().isEmpty()) {
            text.append("\n\nأهم النقاط:");
            draft.keyPoints().stream().filter(p -> p != null && !p.isBlank()).limit(8)
                    .forEach(p -> text.append("\n• ").append(p.trim()));
        }
        lesson.setAiSummary(text.toString());
        lessons.save(lesson);
        return new SummaryView(lesson.getId(), lesson.getAiSummary());
    }

    private String buildPrompt(Lesson lesson, List<LessonMaterial> mats, String notes) {
        String materialList = mats.isEmpty() ? "لا توجد" : mats.stream()
                .map(m -> "- " + m.getType() + ": " + m.getTitle()).reduce((a, b) -> a + "\n" + b).orElse("");
        return """
                أنت مدرّس خبير تكتب ملخصاً للطالب بعد مشاهدة درس بالفيديو. اكتب بالعربية الفصحى المبسّطة،
                بأسلوب واضح ومباشر يساعد الطالب على المراجعة قبل الامتحان.

                عنوان الدرس: %s
                نص الدرس المكتوب (إن وجد): %s
                المواد المرفقة بالدرس:
                %s
                ملاحظات المدرّس / نص الشرح (إن وجد): %s

                اعتمد فقط على المعلومات أعلاه؛ إن كانت قليلة فاكتب ملخصاً عاماً موجزاً للموضوع من عنوانه دون اختراع تفاصيل محددة.
                أرجع النتيجة **فقط** كـJSON صالح بدون أي نص قبله أو بعده، بالشكل التالي بالضبط:
                {"summary": "فقرة من 3 إلى 6 جمل", "keyPoints": ["نقطة 1", "نقطة 2", "نقطة 3"]}
                """.formatted(lesson.getTitle(),
                lesson.getContentText() == null || lesson.getContentText().isBlank() ? "لا يوجد" : lesson.getContentText(),
                materialList, notes.isBlank() ? "لا يوجد" : notes);
    }

    private Draft parse(String content) {
        if (content == null || content.isBlank()) return null;
        String json = content.trim();
        Matcher m = CODE_FENCE.matcher(json);
        if (m.find()) json = m.group(1).trim();
        int start = json.indexOf('{'), end = json.lastIndexOf('}');
        if (start < 0 || end < start) return null;
        try {
            return mapper.readValue(json.substring(start, end + 1), Draft.class);
        } catch (Exception e) {
            log.warn("[AI] failed to parse summary JSON: {}", e.getMessage());
            return null;
        }
    }
}
