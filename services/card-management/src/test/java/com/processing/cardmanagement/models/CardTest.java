package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {

    private static final String PAN = "4000001234567899";

    @ParameterizedTest
    @ValueSource(strings = {"100", "1000"})
    void shouldAllowReservationWhenAmountDoesNotExceedBalance(String amount) {
        // Arrange
        Card card = card(CardStatus.ACTIVE);

        // Act
        Reservation reservation = card.startReservation(new BigDecimal(amount), "123456789012");

        // Assert
        assertThat(reservation.pan()).isEqualTo(PAN);
        assertThat(reservation.reservationAmount()).isEqualByComparingTo(amount);
        assertThat(card.withReservation(reservation).availableBalance()).isEqualByComparingTo(new BigDecimal("1000").subtract(new BigDecimal(amount)));
    }

    @Test
    void shouldRejectReservationWhenBalanceInsufficient() {
        Card card = card(CardStatus.ACTIVE);
        assertThatThrownBy(() -> card.startReservation(new BigDecimal("1001"), "123456789012")).isInstanceOf(InsufficientFundsException.class);
    }

    @ParameterizedTest
    @EnumSource(value = CardStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void shouldRejectReservationWhenCardIsNotActive(CardStatus status) {
        Card card = card(status);
        assertThatThrownBy(() -> card.startReservation(new BigDecimal("100"), "123456789012")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldMarkCardAsDeleted() {
        assertThat(card(CardStatus.ACTIVE).deleted().status()).isEqualTo(CardStatus.DELETED);
    }

    @Test
    void shouldRejectDeletionWhenAlreadyDeleted() {
        assertThatThrownBy(() -> card(CardStatus.DELETED).deleted()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectDailyLimitAboveMonthlyLimit() {
        Card card = card(CardStatus.ACTIVE);
        assertThatThrownBy(() -> card.withData(CardStatus.ACTIVE,
                new BigDecimal("2000"), new BigDecimal("1000"), new BigDecimal("1000"))).isInstanceOf(IllegalArgumentException.class);
    }

    private Card card(CardStatus status) {
        return new Card(UUID.randomUUID(), PAN, "400000", "VIKA VIKTORIA",
                YearMonth.of(2030, 12), status, "643", new BigDecimal("1000"),
                new BigDecimal("10000"), new BigDecimal("1000"), "ISS001",
                Instant.parse("2025-01-01T00:00:00Z"));
    }
}