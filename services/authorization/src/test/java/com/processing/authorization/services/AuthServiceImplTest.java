package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.events.AuthorizationEvent;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.exceptions.InsufficientFundsException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PAN = "4000001234567890";
    private static final String ISSUER = "ISS001";
    private static final YearMonth VALID_EXPIRY = YearMonth.of(2030, 12);

    @Mock private LimitUsageRepository limitUsageRepository;
    @Mock private AuthorizationEventNotifier eventNotifier;
    @Mock private BinLookupClient binLookupClient;
    @Mock private CardManagementClient cardManagementClient;

    private AuthServiceImpl service;
    private Instant startedAt;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(limitUsageRepository, eventNotifier, binLookupClient, cardManagementClient);
        startedAt = Instant.now();
    }

    @Test
    void shouldApproveWhenCardIsActiveAndLimitsAllowPayment() {
        // Arrange
        arrangeCard(card(CardModelStatus.ACTIVE, "10000", VALID_EXPIRY));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse result = service.authorize(request("1000"), startedAt);

        // Assert
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.responseCode()).isEqualTo("00");
        assertThat(result.rrn()).isNotBlank();
        assertThat(result.authCode()).hasSize(6);
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @ParameterizedTest(name = "cardStatus={0}")
    @EnumSource(
            value = CardModelStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "ACTIVE")
    void shouldDeclineWhenCardIsNotActive(CardModelStatus status) {
        // Arrange
        arrangeCard(card(status, "10000", VALID_EXPIRY));

        // Act
        AuthorizationResponse result = service.authorize(request("1000"), startedAt);

        // Assert
        assertThat(result.status()).isEqualTo("DECLINED");
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenCardNotFound() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenThrow(new CardNotFoundException(PAN));

        // Act
        AuthorizationResponse result = service.authorize(request("1000"), startedAt);

        // Assert
        assertThat(result.status()).isEqualTo("DECLINED");
        assertThat(result.declineReason()).isEqualTo("CARD_NOT_FOUND");
        assertThat(result.responseCode()).isEqualTo("14");
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenCardHasExpiredByDate() {
        // Arrange
        arrangeCard(card(CardModelStatus.ACTIVE, "10000", YearMonth.of(2023, 2)));

        // Act
        AuthorizationResponse result = service.authorize(request("1000"), startedAt);

        // Assert
        assertThat(result.declineReason()).isEqualTo("CARD_EXPIRED");
        assertThat(result.responseCode()).isEqualTo("54");
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenBalanceIsInsufficient() {
        // Arrange
        arrangeCard(card(CardModelStatus.ACTIVE, "230", VALID_EXPIRY));

        // Act
        AuthorizationResponse result = service.authorize(request("1000"), startedAt);

        // Assert
        assertThat(result.declineReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.responseCode()).isEqualTo("51");
    }

    @Test
    void shouldDeclineWhenLimitIsExceeded() {
        // Arrange
        arrangeCard(card(CardModelStatus.ACTIVE, "10000", VALID_EXPIRY));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(0);

        // Act
        AuthorizationResponse result = service.authorize(request("1000"), startedAt);

        // Assert
        assertThat(result.declineReason()).isEqualTo("EXCEEDS_AMOUNT_LIMIT");
        assertThat(result.responseCode()).isEqualTo("61");
    }

    @ParameterizedTest(name = "amount={0}")
    @ValueSource(strings = {"999", "1000"})
    void shouldApproveWhenAmountIsAtOrBelowBalance(String amount) {
        // Arrange
        arrangeCard(card(CardModelStatus.ACTIVE, "1000", VALID_EXPIRY));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(2L);

        // Act
        AuthorizationResponse result = service.authorize(request(amount), startedAt);

        // Assert
        assertThat(result.status()).isEqualTo("APPROVED");
    }

    @Test
    void shouldDeclineWhenReservationReportsInsufficientFunds() {
        // Arrange
        arrangeCard(card(CardModelStatus.ACTIVE, "10000", VALID_EXPIRY));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(3L);
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(cardManagementClient).reserve(any(), any(), any());

        // Act
        AuthorizationResponse result = service.authorize(request("1000"), startedAt);

        // Assert
        assertThat(result.declineReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void shouldApproveRollbackWhenCardManagementSucceeds() {
        // Arrange
        RollbackRequest request = new RollbackRequest("123456789012", PAN, new BigDecimal("1000"));

        // Act
        RollbackResponse result = service.rollback(request, startedAt);

        // Assert
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.responseCode()).isEqualTo("00");
        verify(cardManagementClient).rollback(request);
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    private void arrangeCard(CardModel card) {
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER));
    }

    private AuthorizationRequest request(String amount) {
        return new AuthorizationRequest(
                "0100", "000001", PAN, "000000",
                new BigDecimal(amount), "643",
                Instant.parse("2026-11-12T10:00:00Z"),
                "TERM0001", "POS", "MERCH0000000002",
                "5045", "ACQ002", ISSUER
        );
    }

    private CardModel card(CardModelStatus status, String balance, YearMonth expiry) {
        return new CardModel(UUID.randomUUID(), PAN, "400000", "VIKA VIKA", expiry,
                status, "643", new BigDecimal("100000"), new BigDecimal("500000"),
                new BigDecimal(balance), ISSUER, Instant.parse("2025-01-01T00:00:00Z"));
    }
}
