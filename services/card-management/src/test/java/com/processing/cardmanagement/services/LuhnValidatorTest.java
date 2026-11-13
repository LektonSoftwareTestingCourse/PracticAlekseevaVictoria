package com.processing.cardmanagement.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnValidatorTest {

    private final LuhnValidator validator = new LuhnValidator();

    @ParameterizedTest
    @CsvSource({
            "4000001234567899, true",
            "4000001234567890, false",
            "4111111111111111, true",
            "4111111111111112, false"
    })
    void shouldValidatePanByLuhn(String pan, boolean expected) {
        // Act
        boolean actual = validator.isValid(pan);
        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldGenerateSixteenDigitValidPanWithRequestedBin() {
        // Act
        String pan = validator.generatePan("400000");

        // Assert
        assertThat(pan).hasSize(16).startsWith("400000");
        assertThat(validator.isValid(pan)).isTrue();
    }
}
