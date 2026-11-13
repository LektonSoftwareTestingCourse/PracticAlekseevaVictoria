package com.processing.service;

import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggerClientTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private TransactionRequest transaction;
    private LoggerClient loggerClient;

    @BeforeEach
    void setUp() {
        loggerClient = new LoggerClient(rabbitTemplate);
    }

    @Test
    void shouldReturnTrueWhenMessageIsPublished() {
        // Act
        boolean logged = loggerClient.log(transaction);

        // Assert
        assertThat(logged).isTrue();
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY), eq(transaction));
    }

    @Test
    void shouldReturnFalseWhenRabbitMqThrowsException() {
        // Arrange
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));
        // Act
        boolean logged = loggerClient.log(transaction);

        // Assert
        assertThat(logged).isFalse();
    }
}
