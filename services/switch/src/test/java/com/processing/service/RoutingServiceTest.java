package com.processing.service;

import com.processing.config.SwitchProperties;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceTest {

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = new SwitchProperties(
                "1.0.0", Map.of("400000", "ISS001", "400001", "ISS002"),
                null, null, null, null, null, null);
        routingService = new RoutingService(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"400000", "4000001234567890", "4000009999999999"})
    void shouldReturnFirstIssuerWhenBinIsKnown(String pan) {
        // Act
        String issuer = routingService.getIssuerIdByPan(pan);
        // Assert
        assertThat(issuer).isEqualTo("ISS001");
    }

    @Test
    void shouldRouteToAnotherIssuerWhenBinIsDifferent() {
        assertThat(routingService.getIssuerIdByPan("4000011234567890")).isEqualTo("ISS002");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "12345"})
    void shouldRejectNullOrShortPan(String pan) {
        assertThatThrownBy(() -> routingService.getIssuerIdByPan(pan)).isInstanceOf(UnknownBinException.class);
    }

    @Test
    void shouldRejectUnknownBin() {
        assertThatThrownBy(() -> routingService.getIssuerIdByPan("9999991234567890")).isInstanceOf(UnknownBinException.class);
    }
}
