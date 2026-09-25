package com.manarah.common;

import java.util.Map;

/**
 * School years are free text: a teacher types "الصف الثاني الثانوي" on a course, a student picked "تانية ثانوي",
 * an older account says "2 ثانوي". {@link #key} reduces any of those to one comparable key ("secondary-2"), so a
 * student sees exactly the courses of their own year however either side spelled it. Text that names no stage and
 * number (a course "لكل السنين") falls back to its normalized form, which only matches the same wording.
 */
public final class SchoolYears {
    private SchoolYears() {}

    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            Map.entry("اول", 1), Map.entry("اولي", 1), Map.entry("اولا", 1),
            Map.entry("ثاني", 2), Map.entry("ثانيه", 2), Map.entry("تاني", 2), Map.entry("تانيه", 2),
            Map.entry("ثالث", 3), Map.entry("ثالثه", 3), Map.entry("تالت", 3), Map.entry("تالته", 3),
            Map.entry("رابع", 4), Map.entry("رابعه", 4),
            Map.entry("خامس", 5), Map.entry("خامسه", 5),
            Map.entry("سادس", 6), Map.entry("سادسه", 6));

    /** Comparable key for a school year, or "" for blank input. */
    public static String key(String raw) {
        String s = normalize(raw);
        if (s.isEmpty()) return "";
        String stage = s.contains("ثانوي") ? "secondary" : s.contains("اعدادي") ? "preparatory"
                : s.contains("ابتدائي") ? "primary" : s.contains("روضه") || s.contains("kg") ? "kg" : null;
        Integer number = null;
        for (String token : s.split(" ")) {
            // "3ث" / "2ع": the usual shorthand for third secondary / second preparatory.
            if (token.matches("[1-6][ثعب]")) {
                number = token.charAt(0) - '0';
                if (stage == null) stage = switch (token.charAt(1)) { case 'ث' -> "secondary"; case 'ع' -> "preparatory"; default -> "primary"; };
                break;
            }
            if (token.matches("[1-6]")) { number = Integer.parseInt(token); break; }
            String word = token.startsWith("ال") && token.length() > 3 ? token.substring(2) : token;
            if (ORDINALS.containsKey(word)) { number = ORDINALS.get(word); break; }
        }
        // "الثانوية العامة" is how everyone says the final secondary year.
        if ("secondary".equals(stage) && number == null && s.contains("عامه")) number = 3;
        if (stage != null && number != null) return stage + "-" + number;
        return s.replaceAll("(^| )الصف( |$)", " ").trim();
    }

    /** "للصف الثاني الثانوي" / "لـتانية ثانوي": "for <year>" the way it's written in Arabic. */
    public static String forYear(String year) {
        return year.startsWith("ال") ? "لل" + year.substring(2) : "لـ" + year;
    }

    /** Whether two years are the same year. Blank never matches anything. */
    public static boolean same(String a, String b) {
        String ka = key(a);
        return !ka.isEmpty() && ka.equals(key(b));
    }

    /** Folds the spelling variations that don't change meaning: hamza forms, ى/ي, ة/ه, tatweel, diacritics, digits. */
    static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.trim().toLowerCase().toCharArray()) {
            if (c >= 'ً' && c <= 'ْ' || c == 'ـ') continue;
            if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            else if (c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ') c = 'ا';
            else if (c == 'ى') c = 'ي';
            else if (c == 'ة') c = 'ه';
            else if (c == '-' || c == '_' || c == '/' || c == '،' || c == ',') c = ' ';
            out.append(c);
        }
        // "2ثانوي" → "2 ثانوي", so the number and the stage read as separate words.
        return out.toString().replaceAll("([0-9])([^0-9 ][^ ]{2,})", "$1 $2").replaceAll("\\s+", " ").trim();
    }
}
