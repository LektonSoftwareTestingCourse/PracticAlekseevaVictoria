package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRequestNormalizerTest {

    @ParameterizedTest(name = "terminalId={0} -> {1}")
    @CsvSource({
            "TERM0001, TERM0001",
            "TERM, TERM0000",
            "TERM000123, TERM0001"
    })
    void shouldNormalizeTerminalId(String input, String expected) {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldKeepMissingTerminalId(String input) {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId(input)).isEqualTo(input);
    }

    @Test
    void shouldKeepBlankTerminalId() {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId(" ")).isEqualTo(" ");
    }

    @ParameterizedTest(name = "merchantId={0} -> {1}")
    @CsvSource({
            "MERCH0000000002, MERCH0000000002",
            "MERCH00000000002, MERCH0000000002",
            "MERCHANT0000000000000, MERCHANT0000000",
            "STORE00000000001, STORE0000000000",
            "MERCH10000000000, MERCH1000000000",
            "MERCH12, MERCH1200000000"
    })
    void shouldNormalizeMerchantId(String input, String expected) {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldKeepMissingMerchantId(String input) {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId(input)).isEqualTo(input);
    }

    @Test
    void shouldNormalizeBothIdsInRequest() {
        // Arrange
        AuthorizationRequest request = new AuthorizationRequest(
                "0100", "000001", "4000001234567890", "000000", new BigDecimal("100"),
                "643", Instant.parse("2026-11-12T10:00:00Z"), "T1", "POS",
                "MERCH12", "5045", "ACQ002", null);

        // Act
        AuthorizationRequest normalized = AuthorizationRequestNormalizer.normalize(request);

        // Assert
        assertThat(normalized.terminalId()).isEqualTo("T1000000");
        assertThat(normalized.merchantId()).isEqualTo("MERCH1200000000");
        assertThat(normalized.pan()).isEqualTo(request.pan());
        assertThat(normalized.amount()).isEqualByComparingTo(request.amount());
    }
}
