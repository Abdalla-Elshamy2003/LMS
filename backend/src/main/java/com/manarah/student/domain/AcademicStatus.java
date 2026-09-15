package com.manarah.student.domain;

/** Materialised academic standing (§2), derived from overall performance. */
public enum AcademicStatus {
    EXCELLENT("متفوق"),
    GOOD("جيد"),
    AVERAGE("متوسط"),
    NEEDS_ATTENTION("يحتاج متابعة"),
    AT_RISK("معرّض للخطر");

    private final String arabic;

    AcademicStatus(String arabic) {
        this.arabic = arabic;
    }

    public String getArabic() {
        return arabic;
    }

    /** Map an overall percentage to a standing. */
    public static AcademicStatus fromOverall(double overallPercent) {
        if (overallPercent >= 85) return EXCELLENT;
        if (overallPercent >= 70) return GOOD;
        if (overallPercent >= 55) return AVERAGE;
        if (overallPercent >= 40) return NEEDS_ATTENTION;
        return AT_RISK;
    }
}
