package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final String PAN = "4000001234567890";
    private static final BigDecimal FEE = new BigDecimal("3.50");

    @Mock private RoutingService routingService;
    @Mock private AuthorizationClient authorizationClient;
    @Mock private AcquiringFeeClient acquiringFeeClient;
    @Mock private LoggerClient loggerClient;

    private RouteService service;

    @BeforeEach
    void setUp() {
        service = new RouteService(routingService, authorizationClient, acquiringFeeClient, loggerClient);
    }

    @Test
    void shouldApproveAndPublishTransactionWhenAllServicesSucceed() {
        // Arrange
        arrangeSuccessfulAuthorization(approved());
        when(acquiringFeeClient.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(FEE);
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        ArgumentCaptor<TransactionRequest> logged = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(loggerClient).log(logged.capture());
        assertThat(logged.getValue().issuerId()).isEqualTo("ISS001");
        assertThat(logged.getValue().acquiringFee()).isEqualByComparingTo(FEE);
        assertThat(logged.getValue().status().name()).isEqualTo("APPROVED");
        verify(authorizationClient, never()).rollback(any(), any());
    }

    @Test
    void shouldKeepDeclineWithoutRollbackWhenAuthorizationDeclines() {
        // Arrange
        arrangeSuccessfulAuthorization(declined());
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        verify(authorizationClient, never()).rollback(any(), any());
    }

    @Test
    void shouldReturnCardNotFoundWhenBinIsUnknown() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenThrow(new UnknownBinException("400000"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("14");
        ArgumentCaptor<TransactionRequest> logged = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(loggerClient).log(logged.capture());
        assertThat(logged.getValue().status().name()).isEqualTo("DECLINED");
    }

    @Test
    void shouldReturnDeclineWhenAuthorizationIsUnavailable() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenThrow(new AuthorizationException("000001", 3, "timeout"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("05");
    }

    @Test
    void shouldRollbackAndReturnSystemErrorWhenPublishFailsAfterApproval() {
        // Arrange
        arrangeSuccessfulAuthorization(approved());
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), anyString()))
                .thenReturn(new RollbackResponse("123456789012", "00", "APPROVED", null, 1));

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("96");
        verify(authorizationClient).rollback(any(), anyString());
    }

    @Test
    void shouldReturnSystemErrorWhenRollbackHasNoResponse() {
        // Arrange
        arrangeSuccessfulAuthorization(approved());
        when(loggerClient.log(any())).thenReturn(false);

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.responseCode()).isEqualTo("96");
        verify(authorizationClient).rollback(any(), anyString());
    }

    @Test
    void shouldReturnSystemErrorWhenRollbackIsDeclined() {
        // Arrange
        arrangeSuccessfulAuthorization(approved());
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), anyString()))
                .thenReturn(new RollbackResponse("123456789012", "05", "DECLINED", "CONFLICT", 1));

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.responseCode()).isEqualTo("96");
    }

    @Test
    void shouldNotRollbackDeclinedTransactionWhenPublishFails() {
        // Arrange
        arrangeSuccessfulAuthorization(declined());
        when(loggerClient.log(any())).thenReturn(false);

        // Act
        AuthorizationResponse response = service.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("51");
        verify(authorizationClient, never()).rollback(any(), any());
    }

    private void arrangeSuccessfulAuthorization(AuthorizationResponse response) {
        when(routingService.getIssuerIdByPan(PAN)).thenReturn("ISS001");
        when(authorizationClient.authorize(any())).thenReturn(response);
    }

    private AuthorizationRequest validRequest() {
        return new AuthorizationRequest("0100", "000001", PAN, "000000",
                new BigDecimal("1000"), "643", Instant.parse("2026-11-12T10:00:00Z"),
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", null);
    }

    private AuthorizationResponse approved() {
        return new AuthorizationResponse("0110", "000001", "123456789012", "ABC123", "00",
                "APPROVED", null, 3);
    }

    private AuthorizationResponse declined() {
        return new AuthorizationResponse("0110", "000001", null, null, "51",
                "DECLINED", "INSUFFICIENT_FUNDS", null);
    }
}
