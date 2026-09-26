package com.manarah.center;

import com.manarah.common.exception.ApiExceptions.BadRequestException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * The days and hour a group meets. Days are 0 = Sunday … 6 = Saturday, like {@code schedule_slots}; they are listed
 * from Saturday, the first day of the week in Egypt. "Today" is always Cairo's today.
 */
public final class CenterDays {
    public static final ZoneId ZONE = ZoneId.of("Africa/Cairo");
    private static final List<String> NAMES = List.of("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت");
    private static final List<Integer> WEEK_ORDER = List.of(6, 0, 1, 2, 3, 4, 5);
    private static final String TIME = "([01][0-9]|2[0-3]):[0-5][0-9]";

    private CenterDays() {}

    public static LocalDate today() { return LocalDate.now(ZONE); }

    public static int dayOf(LocalDate date) { return date.getDayOfWeek().getValue() % 7; }

    public static String name(int day) { return NAMES.get(day); }

    public static List<Integer> parse(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Integer> out = new ArrayList<>();
        for (String part : csv.split(",")) if (!part.isBlank()) out.add(Integer.parseInt(part.trim()));
        return out;
    }

    /** Validated, de-duplicated and in week order, ready to store. */
    public static String format(List<Integer> days) {
        if (days == null || days.isEmpty()) throw new BadRequestException("اختار يوم واحد على الأقل للمجموعة");
        Set<Integer> set = new HashSet<>();
        for (Integer d : days) {
            if (d == null || d < 0 || d > 6) throw new BadRequestException("يوم غير صحيح");
            set.add(d);
        }
        return WEEK_ORDER.stream().filter(set::contains).map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
    }

    public static boolean meets(String csv, LocalDate date) { return parse(csv).contains(dayOf(date)); }

    /** "السبت والثلاثاء" */
    public static String label(String csv) {
        List<String> names = parse(csv).stream().map(CenterDays::name).toList();
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        return String.join("، ", names.subList(0, names.size() - 1)) + " و" + names.get(names.size() - 1);
    }

    public static String time(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new BadRequestException("اكتب ميعاد الحصة");
            return null;
        }
        String t = value.trim();
        if (t.length() == 4) t = "0" + t;
        if (!t.matches(TIME)) throw new BadRequestException("الميعاد لازم يبقى بالشكل 16:00");
        return t;
    }

    /** "4:00 م" (or "4:00 م – 6:00 م") */
    public static String timeLabel(String start, String end) {
        return end == null || end.isBlank() ? clock(start) : clock(start) + " – " + clock(end);
    }

    private static String clock(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return "";
        int h = Integer.parseInt(hhmm.substring(0, 2));
        String m = hhmm.substring(3, 5);
        int twelve = h % 12 == 0 ? 12 : h % 12;
        return twelve + ":" + m + (h < 12 ? " ص" : " م");
    }
}
