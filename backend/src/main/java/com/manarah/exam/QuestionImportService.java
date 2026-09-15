package com.manarah.exam;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.exam.ExamDtos.CreateQuestionRequest;
import com.manarah.exam.ExamDtos.OptionInput;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bulk-loads bank questions from a CSV sheet (Excel → "CSV UTF-8"). One row per question; the
 * header row may be in Arabic or English. Rows are saved one at a time so a bad row is reported
 * with its line number instead of failing the whole file.
 */
@Service
public class QuestionImportService {

    public record RowError(int row, String message) {}
    public record ImportResult(int created, int failed, List<RowError> errors) {}

    private static final Map<String, String> HEADERS = Map.ofEntries(
            Map.entry("type", "type"), Map.entry("النوع", "type"), Map.entry("نوع", "type"),
            Map.entry("difficulty", "difficulty"), Map.entry("الصعوبة", "difficulty"), Map.entry("صعوبة", "difficulty"),
            Map.entry("subject", "subject"), Map.entry("المادة", "subject"), Map.entry("مادة", "subject"),
            Map.entry("chapter", "chapter"), Map.entry("الفصل", "chapter"), Map.entry("الوحدة", "chapter"), Map.entry("فصل", "chapter"),
            Map.entry("stem", "stem"), Map.entry("question", "stem"), Map.entry("السؤال", "stem"), Map.entry("سؤال", "stem"), Map.entry("نص السؤال", "stem"),
            Map.entry("points", "points"), Map.entry("الدرجة", "points"), Map.entry("درجة", "points"),
            Map.entry("correct", "correct"), Map.entry("الصحيح", "correct"), Map.entry("الإجابة الصحيحة", "correct"), Map.entry("صح", "correct"),
            Map.entry("answer", "answer"), Map.entry("الإجابة", "answer"), Map.entry("الاجابة", "answer"), Map.entry("إجابة", "answer"),
            Map.entry("explanation", "explanation"), Map.entry("الشرح", "explanation"), Map.entry("شرح", "explanation"),
            Map.entry("tags", "tags"), Map.entry("وسوم", "tags"), Map.entry("الوسوم", "tags"));

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("MCQ", "MCQ"), Map.entry("اختيار", "MCQ"), Map.entry("اختيار من متعدد", "MCQ"), Map.entry("اختياري", "MCQ"),
            Map.entry("TRUE_FALSE", "TRUE_FALSE"), Map.entry("صح/خطأ", "TRUE_FALSE"), Map.entry("صح خطأ", "TRUE_FALSE"), Map.entry("صح او خطأ", "TRUE_FALSE"), Map.entry("صح أو خطأ", "TRUE_FALSE"),
            Map.entry("MULTI_SELECT", "MULTI_SELECT"), Map.entry("متعدد الإجابات", "MULTI_SELECT"), Map.entry("متعدد", "MULTI_SELECT"),
            Map.entry("FILL_BLANK", "FILL_BLANK"), Map.entry("أكمل", "FILL_BLANK"), Map.entry("اكمل", "FILL_BLANK"), Map.entry("أكمل الفراغ", "FILL_BLANK"),
            Map.entry("SHORT_ANSWER", "SHORT_ANSWER"), Map.entry("إجابة قصيرة", "SHORT_ANSWER"), Map.entry("قصيرة", "SHORT_ANSWER"),
            Map.entry("NUMERIC", "NUMERIC"), Map.entry("رقمي", "NUMERIC"), Map.entry("رقم", "NUMERIC"),
            Map.entry("ESSAY", "ESSAY"), Map.entry("مقالي", "ESSAY"), Map.entry("مقال", "ESSAY"));

    private static final Map<String, String> DIFF = Map.of(
            "EASY", "EASY", "سهل", "EASY", "سهلة", "EASY",
            "MEDIUM", "MEDIUM", "متوسط", "MEDIUM", "متوسطة", "MEDIUM",
            "HARD", "HARD", "صعب", "HARD", "صعبة", "HARD");

    private final ExamService exams;

    public QuestionImportService(ExamService exams) { this.exams = exams; }

    public ImportResult importCsv(UserPrincipal actor, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("اختر ملف CSV");
        if (file.getSize() > 5L * 1024 * 1024) throw new BadRequestException("الملف أكبر من 5 ميجابايت");
        String text;
        try { text = new String(file.getBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new BadRequestException("تعذّر قراءة الملف"); }
        if (text.startsWith("﻿")) text = text.substring(1);

        List<List<String>> rows = parseCsv(text);
        if (rows.size() < 2) throw new BadRequestException("الملف لا يحتوي على أسئلة — الصف الأول للعناوين ويليه سؤال في كل صف");

        Map<String, Integer> col = new HashMap<>();
        List<Integer> optionCols = new ArrayList<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).trim().toLowerCase(Locale.ROOT);
            String key = HEADERS.get(h);
            if (key != null) { col.put(key, i); continue; }
            if (h.matches("(option|choice|اختيار|الاختيار|خيار)\\s*_?\\s*\\d+")) optionCols.add(i);
        }
        if (!col.containsKey("stem")) throw new BadRequestException("لم أجد عمود السؤال — سمِّه \"السؤال\" أو \"stem\"");
        if (optionCols.size() > 10) throw new BadRequestException("الحد الأقصى 10 اختيارات لكل سؤال");

        int created = 0;
        List<RowError> errors = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(String::isBlank)) continue;
            try {
                exams.createQuestion(actor, toRequest(row, col, optionCols));
                created++;
            } catch (BadRequestException e) {
                errors.add(new RowError(r + 1, e.getMessage()));
            } catch (Exception e) {
                errors.add(new RowError(r + 1, "صف غير صالح"));
            }
            if (errors.size() >= 50) { errors.add(new RowError(r + 1, "توقفت بعد 50 خطأ — راجع الملف"));  break; }
        }
        return new ImportResult(created, errors.size(), errors);
    }

    private CreateQuestionRequest toRequest(List<String> row, Map<String, Integer> col, List<Integer> optionCols) {
        String typeRaw = cell(row, col.get("type"));
        String type = typeRaw.isBlank() ? "MCQ" : TYPES.get(typeRaw.trim());
        if (type == null) type = TYPES.get(typeRaw.trim().toUpperCase(Locale.ROOT));
        if (type == null) throw new BadRequestException("نوع السؤال غير معروف: " + typeRaw);
        String diffRaw = cell(row, col.get("difficulty"));
        String difficulty = diffRaw.isBlank() ? "MEDIUM" : DIFF.get(diffRaw.trim().toUpperCase(Locale.ROOT).equals(diffRaw.trim()) ? diffRaw.trim() : diffRaw.trim());
        if (difficulty == null) difficulty = DIFF.get(diffRaw.trim().toUpperCase(Locale.ROOT));
        if (difficulty == null) throw new BadRequestException("الصعوبة غير معروفة: " + diffRaw + " (سهل / متوسط / صعب)");
        String stem = cell(row, col.get("stem")).trim();
        if (stem.isBlank()) throw new BadRequestException("نص السؤال فارغ");
        Double points = null;
        String p = cell(row, col.get("points")).trim();
        if (!p.isBlank()) {
            try { points = Double.parseDouble(normalizeDigits(p)); } catch (NumberFormatException e) { throw new BadRequestException("الدرجة ليست رقماً: " + p); }
        }

        List<OptionInput> options = null;
        String answer = null;
        boolean needsOptions = Set.of("MCQ", "TRUE_FALSE", "MULTI_SELECT").contains(type);
        if (needsOptions) {
            List<String> texts = new ArrayList<>();
            if ("TRUE_FALSE".equals(type) && optionCols.stream().map(i -> cell(row, i)).allMatch(String::isBlank)) texts.addAll(List.of("صح", "خطأ"));
            else for (Integer i : optionCols) { String t = cell(row, i).trim(); if (!t.isBlank()) texts.add(t); }
            if (texts.size() < 2) throw new BadRequestException("السؤال يحتاج اختيارين على الأقل");
            Set<Integer> correct = parseCorrect(cell(row, col.get("correct")), texts);
            if (correct.isEmpty()) throw new BadRequestException("حدد الإجابة الصحيحة (رقم الاختيار، مثل 2)");
            if (!"MULTI_SELECT".equals(type) && correct.size() > 1) throw new BadRequestException("هذا النوع يقبل إجابة صحيحة واحدة فقط");
            options = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) options.add(new OptionInput(texts.get(i), correct.contains(i + 1), i));
        } else if (!"ESSAY".equals(type)) {
            answer = cell(row, col.get("answer")).trim();
            if (answer.isBlank()) answer = cell(row, col.get("correct")).trim();
            if (answer.isBlank()) throw new BadRequestException("اكتب الإجابة النموذجية في عمود \"الإجابة\"");
        }
        return new CreateQuestionRequest(nn(cell(row, col.get("subject"))), nn(cell(row, col.get("chapter"))), null,
                difficulty, type, stem, points, answer, null, nn(cell(row, col.get("tags"))), options,
                nn(cell(row, col.get("explanation"))), null);
    }

    /** "2", "1,3", "٢", "ب", "B" or the option's own text all mark which options are correct. */
    private static Set<Integer> parseCorrect(String raw, List<String> texts) {
        Set<Integer> out = new TreeSet<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : normalizeDigits(raw).split("[,،;|/ ]+")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            if (t.matches("\\d+")) { int n = Integer.parseInt(t); if (n >= 1 && n <= texts.size()) out.add(n); continue; }
            String letters = "abcdefghij", arabic = "أبجدهوزحطي";
            int li = letters.indexOf(t.toLowerCase(Locale.ROOT)), ai = arabic.indexOf(t.replace("ا", "أ"));
            if (t.length() == 1 && li >= 0 && li < texts.size()) { out.add(li + 1); continue; }
            if (t.length() == 1 && ai >= 0 && ai < texts.size()) { out.add(ai + 1); continue; }
            for (int i = 0; i < texts.size(); i++) if (texts.get(i).trim().equalsIgnoreCase(t)) out.add(i + 1);
        }
        return out;
    }

    private static String cell(List<String> row, Integer i) { return i == null || i >= row.size() ? "" : Objects.toString(row.get(i), ""); }
    private static String nn(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String normalizeDigits(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + ch - '٠'));
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + ch - '۰'));
            else b.append(ch == '٫' ? '.' : ch);
        }
        return b.toString();
    }

    /** Minimal RFC-4180 reader: quoted fields, doubled quotes, CR/LF line ends, comma or semicolon separators. */
    static List<List<String>> parseCsv(String text) {
        char sep = detectSeparator(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; } else quoted = false; }
                else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == sep) { row.add(cur.toString()); cur.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cur.toString()); cur.setLength(0); rows.add(row); row = new ArrayList<>();
            } else cur.append(c);
        }
        if (cur.length() > 0 || !row.isEmpty()) { row.add(cur.toString()); rows.add(row); }
        return rows;
    }

    private static char detectSeparator(String text) {
        int nl = text.indexOf('\n');
        String first = nl < 0 ? text : text.substring(0, nl);
        long commas = first.chars().filter(c -> c == ',').count(), semis = first.chars().filter(c -> c == ';').count();
        return semis > commas ? ';' : ',';
    }
}
