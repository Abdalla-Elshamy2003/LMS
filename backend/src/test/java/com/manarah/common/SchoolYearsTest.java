package com.manarah.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolYearsTest {

    @Test void theSameYearHoweverItIsWritten() {
        for (String s : new String[]{"الصف الثاني الثانوي", "الثاني الثانوي", "تانية ثانوي", "ثانية ثانوى", "2 ثانوي",
                "٢ ثانوي", "2ثانوي", "٢ث", "الصف الثانى الثانوى", "  الصف   الثاني   الثانوي "})
            assertThat(SchoolYears.key(s)).as(s).isEqualTo("secondary-2");
        assertThat(SchoolYears.key("الصف الأول الثانوي")).isEqualTo("secondary-1");
        assertThat(SchoolYears.key("أولى ثانوي")).isEqualTo("secondary-1");
        assertThat(SchoolYears.key("الصف الثالث الثانوي")).isEqualTo("secondary-3");
        assertThat(SchoolYears.key("الثانوية العامة")).isEqualTo("secondary-3");
        assertThat(SchoolYears.key("3ث")).isEqualTo("secondary-3");
        assertThat(SchoolYears.key("الصف الأول الإعدادي")).isEqualTo("preparatory-1");
        assertThat(SchoolYears.key("تالتة إعدادي")).isEqualTo("preparatory-3");
        assertThat(SchoolYears.key("الصف السادس الابتدائي")).isEqualTo("primary-6");
        assertThat(SchoolYears.key("روضة أولى")).isEqualTo("kg-1");
    }

    @Test void differentYearsAndBlanksNeverMatch() {
        assertThat(SchoolYears.same("الصف الأول الثانوي", "الصف الثاني الثانوي")).isFalse();
        assertThat(SchoolYears.same("الصف الأول الثانوي", "الصف الأول الإعدادي")).isFalse();
        assertThat(SchoolYears.same(null, "الصف الأول الثانوي")).isFalse();
        assertThat(SchoolYears.same("", "")).isFalse();
        assertThat(SchoolYears.same("تانية ثانوي", "الصف الثاني الثانوي")).isTrue();
        // No stage and number to read: only the same wording matches.
        assertThat(SchoolYears.same("مراجعة نهائية", "مراجعة  نهائية")).isTrue();
        assertThat(SchoolYears.same("مراجعة نهائية", "تأسيس")).isFalse();
    }
}
