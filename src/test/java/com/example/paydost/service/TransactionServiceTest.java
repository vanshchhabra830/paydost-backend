package com.example.paydost.service;

import com.example.paydost.dto.TransactionResponseDto;
import com.example.paydost.dto.TransferRequestDto;
import com.example.paydost.exception.InsufficientBalanceException;
import com.example.paydost.model.Transaction;
import com.example.paydost.model.TransactionStatus;
import com.example.paydost.model.User;
import com.example.paydost.model.Wallet;
import com.example.paydost.repository.TransactionRepository;
import com.example.paydost.repository.UserRepository;
import com.example.paydost.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private TransactionService transactionService;

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;
    private TransferRequestDto request;

    @BeforeEach
    void setUp() {
        sender = User.builder().id(1L).email("sender@example.com").build();
        receiver = User.builder().id(2L).email("receiver@example.com").build();
        senderWallet = Wallet.builder().id(1L).user(sender).balance(new BigDecimal("1000.00")).build();
        receiverWallet = Wallet.builder().id(2L).user(receiver).balance(new BigDecimal("500.00")).build();
        
        request = TransferRequestDto.builder()
                .receiverEmail("receiver@example.com")
                .amount(new BigDecimal("300.00"))
                .referenceId("tx-12345")
                .build();
    }

    @Test
    void transfer_Successful() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("txn:tx-12345")).thenReturn(null); // Cache miss
        when(transactionRepository.findByReferenceId("tx-12345")).thenReturn(Optional.empty()); // DB miss

        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("receiver@example.com")).thenReturn(Optional.of(receiver));
        
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(receiverWallet));

        // Act
        TransactionResponseDto response = transactionService.transfer("sender@example.com", request);

        // Assert
        assertNotNull(response);
        assertEquals(new BigDecimal("300.00"), response.getAmount());
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        
        // Verify balances updated
        assertEquals(new BigDecimal("700.00"), senderWallet.getBalance());
        assertEquals(new BigDecimal("800.00"), receiverWallet.getBalance());

        // Verify saves
        verify(walletRepository).save(senderWallet);
        verify(walletRepository).save(receiverWallet);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void transfer_InsufficientBalance_ThrowsException() {
        // Arrange
        request.setAmount(new BigDecimal("2000.00")); // More than sender's 1000.00
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("txn:tx-12345")).thenReturn(null);
        when(transactionRepository.findByReferenceId("tx-12345")).thenReturn(Optional.empty());

        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("receiver@example.com")).thenReturn(Optional.of(receiver));
        
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(receiverWallet));

        // Act & Assert
        assertThrows(InsufficientBalanceException.class, () -> {
            transactionService.transfer("sender@example.com", request);
        });

        // Verify no saves
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_DuplicateReferenceId_ReturnsCachedResult() {
        // Arrange
        TransactionResponseDto cachedResponse = TransactionResponseDto.builder()
                .id(1L)
                .amount(new BigDecimal("300.00"))
                .referenceId("tx-12345")
                .status(TransactionStatus.SUCCESS)
                .build();
                
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("txn:tx-12345")).thenReturn(cachedResponse); // Cache hit

        // Act
        TransactionResponseDto response = transactionService.transfer("sender@example.com", request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        
        // Verify we never hit DB or perform transfer logic
        verify(transactionRepository, never()).findByReferenceId(any());
        verify(userRepository, never()).findByEmail(any());
        verify(transactionRepository, never()).save(any());
    }
}
