package com.manarah.student.scan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScannedCodeTest {

    @Test
    void bareCodesAreOnlyTrimmed() {
        assertThat(ScannedCode.normalize("  abc123  ")).isEqualTo("abc123");
        assertThat(ScannedCode.normalize("STD-00042")).isEqualTo("STD-00042");
    }

    @Test
    void urlsYieldTheirLastPathSegment() {
        assertThat(ScannedCode.normalize("https://h.example/student/verify/abc123")).isEqualTo("abc123");
        assertThat(ScannedCode.normalize("https://h.example/app/gate/abc123")).isEqualTo("abc123");
        assertThat(ScannedCode.normalize("/student/verify/abc123/")).isEqualTo("abc123");
    }

    @Test
    void queryAndFragmentAreDropped() {
        assertThat(ScannedCode.normalize("https://h.example/student/verify/abc123?utm=1#top")).isEqualTo("abc123");
    }

    @Test
    void emptyInputStaysEmpty() {
        assertThat(ScannedCode.normalize(null)).isEmpty();
        assertThat(ScannedCode.normalize("   ")).isEmpty();
    }
}
