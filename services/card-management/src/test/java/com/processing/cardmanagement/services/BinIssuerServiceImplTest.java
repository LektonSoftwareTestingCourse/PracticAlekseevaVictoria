package com.processing.cardmanagement.services;

import com.processing.cardmanagement.exceptions.BinAlreadyExistException;
import com.processing.cardmanagement.exceptions.BinNotFoundException;
import com.processing.cardmanagement.models.BinIssuer;
import com.processing.cardmanagement.repositories.BinIssuerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinIssuerServiceImplTest {

    @Mock private BinIssuerRepository repository;
    private BinIssuerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BinIssuerServiceImpl(repository);
    }

    @Test
    void shouldReturnIssuerWhenBinExists() {
        // Arrange
        when(repository.findByBin("400000")).thenReturn(Optional.of(new BinIssuer("400000", "ISS001")));

        // Act
        String issuerId = service.getIssuerId("400000");

        // Assert
        assertThat(issuerId).isEqualTo("ISS001");
    }

    @Test
    void shouldThrowWhenBinDoesNotExist() {
        // Arrange
        when(repository.findByBin("999999")).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.getIssuerId("999999")).isInstanceOf(BinNotFoundException.class);
    }

    @Test
    void shouldCreateWhenBinIsUnique() {
        // Arrange
        BinIssuer created = new BinIssuer("400000", "ISS001");
        when(repository.existsByBin("400000")).thenReturn(false);
        when(repository.save(created)).thenReturn(created);

        // Act
        BinIssuer result = service.create("400000", "ISS001");

        // Assert
        assertThat(result).isEqualTo(created);
        verify(repository).save(created);
    }

    @Test
    void shouldRejectDuplicateBin() {
        // Arrange
        when(repository.existsByBin("400000")).thenReturn(true);

        // Act+assert
        assertThatThrownBy(() -> service.create("400000", "ISS001")).isInstanceOf(BinAlreadyExistException.class);
    }

    @Test
    void shouldDeleteKnownBin() {
        // Arrange
        when(repository.existsByBin("400000")).thenReturn(true);

        // Act
        service.delete("400000");

        // Assert
        verify(repository).deleteByBin("400000");
    }

    @Test
    void shouldRejectDeletionOfUnknownBin() {
        // Arrange
        when(repository.existsByBin("999999")).thenReturn(false);

        // Act +assert
        assertThatThrownBy(() -> service.delete("999999")).isInstanceOf(BinNotFoundException.class);
    }
}
