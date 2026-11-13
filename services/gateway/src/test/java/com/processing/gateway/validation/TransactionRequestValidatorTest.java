package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRequestValidatorTest {

    private final TransactionRequestValidator validator = new TransactionRequestValidator();

    @Test
    void shouldAcceptValidAuthorizationRequest() {
        // Act + Assert
        assertThatCode(() -> validator.validate(request("0100", "4000001234567890",
                new BigDecimal("1000"), "643", "5045"))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullRequest() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(TransactionValidationException.class).hasMessage("Request body is required");
    }

    @ParameterizedTest
    @CsvSource({"12345", "123456789012345", "12345678901234567", "123456789012345a"})
    void shouldRejectInvalidPan(String pan) {
        assertThatThrownBy(() -> validator.validate(request("0100", pan,
                new BigDecimal("1000"), "643", "5045")))
                .isInstanceOf(TransactionValidationException.class).hasMessageContaining("pan");
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "-100"})
    void shouldRejectNonPositiveAmount(String amount) {
        assertThatThrownBy(() -> validator.validate(request("0100", "4000001234567890",
                new BigDecimal(amount), "643", "5045")))
                .isInstanceOf(TransactionValidationException.class).hasMessageContaining("amount");
    }

    @Test
    void shouldRejectMissingAmount() {
        assertThatThrownBy(() -> validator.validate(request("0100", "4000001234567890",
                null, "643", "5045")))
                .isInstanceOf(TransactionValidationException.class).hasMessageContaining("amount");
    }

    @ParameterizedTest
    @CsvSource({"64", "6430"})
    void shouldRejectInvalidCurrencyCode(String currencyCode) {
        assertThatThrownBy(() -> validator.validate(request("0100", "4000001234567890",
                new BigDecimal("1000"), currencyCode, "5045")))
                .isInstanceOf(TransactionValidationException.class).hasMessageContaining("currencyCode");
    }

    @ParameterizedTest
    @CsvSource({"123", "12345", "123a"})
    void shouldRejectInvalidMcc(String mcc) {
        assertThatThrownBy(() -> validator.validate(request("0100", "4000001234567890",
                new BigDecimal("1000"), "643", mcc)))
                .isInstanceOf(TransactionValidationException.class).hasMessageContaining("mcc");
    }

    private AuthorizationRequest request(String mti, String pan, BigDecimal amount, String currency, String mcc) {
        return new AuthorizationRequest(mti, "000001", pan, "000000", amount, currency,
                Instant.parse("2026-11-12T10:00:00Z"), "TERM0001", "POS",
                "MERCH0000000002", mcc, "ACQ002", null);
    }
}