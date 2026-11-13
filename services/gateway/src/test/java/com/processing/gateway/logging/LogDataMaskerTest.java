package com.processing.gateway.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogDataMaskerTest {

    private final LogDataMasker masker = new LogDataMasker();

    @Test
    void shouldMaskPanAndCvvInJson() {
        // Arrange
        String input = "{\"pan\":\"4000001234567890\",\"cvv\":\"123\"}";

        // Act
        String result = masker.maskData(input);

        // Assert
        assertThat(result).contains("400000****7890");
        assertThat(result).contains("\"cvv\":\"***\"");
        assertThat(result).doesNotContain("4000001234567890", "\"123\"");
    }

    @Test
    void shouldLeaveOrdinaryTextUnchanged() {
        assertThat(masker.maskData("transaction approved")).isEqualTo("transaction approved");
    }
}