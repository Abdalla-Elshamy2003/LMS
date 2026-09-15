package com.manarah.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-generated performance summary for one student's profile — reuses the same
 * OpenAI-compatible chat-completions config as {@link com.manarah.exam.AiQuestionGenerator}
 * (`manarah.ai.*` / `MANARAH_AI_*`), since it's the same underlying capability applied to a
 * different prompt. Kept as its own small service rather than sharing code with the exam
 * generator — the two have different request/response shapes and evolve independently.
 */
@Service
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestClient restClient = com.manarah.common.ai.AiHttp.client();
    private final ObjectMapper mapper = new ObjectMapper();

    public AiInsightService(
            @Value("${manarah.ai.enabled:false}") boolean enabled,
            @Value("${manarah.ai.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${manarah.ai.api-key:}") String apiKey,
            @Value("${manarah.ai.model:inclusionai/ling-3.0-flash-fin:free}") String model) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public record Insight(List<String> strengths, List<String> weaknesses, List<String> recommendations) {
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public Insight forStudent(String fullName, double overallPercent, double attendanceRate, double homeworkRate,
                              double avgScore, String academicStatus, List<String> riskReasons) {
        if (!isConfigured()) {
            throw new BadRequestException("تحليل الأداء بالذكاء الاصطناعي غير مُفعّل — يحتاج إعداد مفتاح API أولاً");
        }
        String prompt = buildPrompt(fullName, overallPercent, attendanceRate, homeworkRate, avgScore,
                academicStatus, riskReasons);
        String content;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.5);
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
                throw new BadRequestException("لم يتمكن النموذج من تحليل أداء الطالب، حاول مرة أخرى");
            }
            content = choices.get(0).path("message").path("content").asText("");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] student insight failed: {}", e.getMessage());
            throw new BadRequestException("تعذّر الاتصال بخدمة الذكاء الاصطناعي، حاول مرة أخرى");
        }

        Insight insight = parse(content);
        if (insight == null) {
            throw new BadRequestException("لم يتمكن النموذج من إنتاج تحليل صالح، حاول مرة أخرى");
        }
        return insight;
    }

    private String buildPrompt(String fullName, double overallPercent, double attendanceRate, double homeworkRate,
                               double avgScore, String academicStatus, List<String> riskReasons) {
        String reasons = (riskReasons == null || riskReasons.isEmpty()) ? "لا توجد" : String.join("، ", riskReasons);
        return """
                أنت مستشار تعليمي يحلل أداء طالب بناءً على بيانات حقيقية من نظام إدارة تعليمي، وتكتب تحليلك
                بالعربية الفصحى لولي أمر أو مدرّس.

                بيانات الطالب "%s":
                - المعدل العام: %.1f%%
                - نسبة الحضور: %.1f%%
                - نسبة إنجاز الواجبات: %.1f%%
                - متوسط درجات الامتحانات: %.1f%%
                - الحالة الأكاديمية الحالية: %s
                - أسباب مسجَّلة للمتابعة (إن وجدت): %s

                اكتب تحليلاً موجزاً وعملياً — لا تخترع بيانات غير موجودة أعلاه، اعتمد فقط على الأرقام المعطاة.
                أرجع النتيجة **فقط** كـJSON صالح بدون أي نص إضافي قبله أو بعده، بالشكل التالي بالضبط:
                {"strengths": ["نقطة قوة 1", "نقطة قوة 2"], "weaknesses": ["نقطة ضعف 1"], "recommendations": ["توصية 1", "توصية 2"]}
                كل مصفوفة تحتوي 2 إلى 3 عناصر قصيرة وواضحة.
                """.formatted(fullName, overallPercent, attendanceRate, homeworkRate, avgScore,
                academicStatus == null ? "غير محدّدة" : academicStatus, reasons);
    }

    private Insight parse(String content) {
        if (content == null || content.isBlank()) return null;
        String json = content.trim();
        Matcher m = CODE_FENCE.matcher(json);
        if (m.find()) json = m.group(1).trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) return null;
        json = json.substring(start, end + 1);
        try {
            return mapper.readValue(json, Insight.class);
        } catch (Exception e) {
            log.warn("[AI] failed to parse insight JSON: {}", e.getMessage());
            return null;
        }
    }
}
