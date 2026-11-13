package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.CardEvent;
import com.processing.cardmanagement.events.CardEventNotifier;
import com.processing.cardmanagement.exceptions.CardNotFoundException;
import com.processing.cardmanagement.exceptions.TooLargeLimitException;
import com.processing.cardmanagement.models.Card;
import com.processing.cardmanagement.options.CardServiceDefaults;
import com.processing.cardmanagement.options.CardServiceSettings;
import com.processing.cardmanagement.repositories.CardRepository;
import com.processing.cardmanagement.repositories.ReservationRepository;
import com.processing.cardmanagement.repositories.ReservationRollbackRepository;
import com.processing.cardmanagement.services.retries.RetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock private CardRepository cardRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationRollbackRepository reservationRollbackRepository;
    @Mock private CardServiceSettings settings;
    @Mock private CardServiceDefaults defaults;
    @Mock private PanGenerator panGenerator;
    @Mock private CardEventNotifier eventNotifier;
    @Mock private BinIssuerService binIssuerService;
    @Mock private RetryService retryService;
    @Mock private TransactionRunner transactionRunner;
    @Mock private Card card;

    private CardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CardServiceImpl(cardRepository, reservationRepository,
                reservationRollbackRepository, settings, defaults, panGenerator,
                eventNotifier, binIssuerService, retryService, transactionRunner);
    }

    @Test
    void shouldReturnCardWhenPanExists() {
        // Arrange
        when(cardRepository.findByPan("4000001234567899")).thenReturn(Optional.of(card));

        // Act
        Card result = service.getCard("4000001234567899");

        // Assert
        assertThat(result).isSameAs(card);
    }

    @Test
    void shouldThrowWhenCardDoesNotExist() {
        // Arrange
        when(cardRepository.findByPan("9999991234567899")).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.getCard("9999991234567899")).isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void shouldRejectPageLimitAboveConfiguredMaximum() {
        // Arrange
        when(settings.maxPageLimit()).thenReturn(100);

        // Act + Assert
        assertThatThrownBy(() -> service.getCards(101, 0L, null, null, null, null, null)).isInstanceOf(TooLargeLimitException.class);
    }

    @Test
    void shouldRejectBulkUpdateWithoutBinsOrPans() {
        assertThatThrownBy(() -> service.bulkUpdateStatus(null, null, com.processing.cardmanagement.models.CardStatus.BLOCKED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBulkUpdateWithBinsAndPansTogether() {
        assertThatThrownBy(() -> service.bulkUpdateStatus(List.of("400000"),
                List.of("4000001234567899"), com.processing.cardmanagement.models.CardStatus.BLOCKED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotifyWhenCountingAllCards() {
        // Arrange
        when(cardRepository.countAllCards()).thenReturn(5L);

        // Act
        long count = service.countAllCardsAndUpdate();

        // Assert
        assertThat(count).isEqualTo(5L);
        verify(eventNotifier).notifyListeners(any(CardEvent.class));
    }
}
