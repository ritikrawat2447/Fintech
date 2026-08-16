package com.extradict.fintechapi.service;

import com.extradict.fintechapi.dto.TransactionRequest;
import com.extradict.fintechapi.dto.TransactionResponse;
import com.extradict.fintechapi.entity.Account;
import com.extradict.fintechapi.entity.IdempotencyKey;
import com.extradict.fintechapi.entity.Transaction;
import com.extradict.fintechapi.entity.User;
import com.extradict.fintechapi.enums.AccountStatus;
import com.extradict.fintechapi.enums.TransactionStatus;
import com.extradict.fintechapi.repository.AccountRepository;
import com.extradict.fintechapi.repository.IdempotencyKeyRepository;
import com.extradict.fintechapi.repository.TransactionRepository;
import com.extradict.fintechapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.extradict.fintechapi.notification.TransactionNotificationPublisher;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Mock
    private TransactionNotificationPublisher notificationPublisher;

    private User user;
    private Account fromAccount;
    private Account toAccount;
    private UUID fromAccountId;
    private UUID toAccountId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ritik@gmail.com");
        user.setName("Ritik");
        user.setPassword("hashed");

        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();

        fromAccount = new Account();
        fromAccount.setId(fromAccountId);
        fromAccount.setUser(user);
        fromAccount.setBalance(new BigDecimal("10000.00"));
        fromAccount.setStatus(AccountStatus.ACTIVE);
        fromAccount.setCurrency("INR");

        toAccount = new Account();
        toAccount.setId(toAccountId);
        toAccount.setBalance(new BigDecimal("0.00"));
        toAccount.setStatus(AccountStatus.ACTIVE);
        toAccount.setCurrency("INR");

        User toUser = new User();
        toUser.setId(UUID.randomUUID());
        toUser.setEmail("user2@gmail.com");
        toAccount.setUser(toUser);
    }

    // ── Test 1: Happy path transfer ───────────────────────────
    @Test
    void shouldProcessTransferSuccessfully() {
        // Arrange
        TransactionRequest request = new TransactionRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setAmount(new BigDecimal("500.00"));
        request.setCurrency("INR");
        request.setDescription("Test payment");
        request.setIdempotencyKey("unique-key-001");

        when(idempotencyKeyRepository.findByKeyAndExpiresAtAfter(
                any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdWithLock(fromAccountId))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId))
                .thenReturn(Optional.of(toAccount));
        when(userRepository.findByEmail("ritik@gmail.com"))
                .thenReturn(Optional.of(user));

        Transaction savedTxn = new Transaction();
        savedTxn.setId(UUID.randomUUID());
        savedTxn.setFromAccount(fromAccount);
        savedTxn.setToAccount(toAccount);
        savedTxn.setAmount(new BigDecimal("500.00"));
        savedTxn.setCurrency("INR");
        savedTxn.setStatus(TransactionStatus.SUCCESS);
        savedTxn.setIdempotencyKey("unique-key-001");
        savedTxn.setProcessedAt(LocalDateTime.now());

        when(transactionRepository.save(any())).thenReturn(savedTxn);
        when(accountRepository.save(any())).thenReturn(fromAccount);

        // Act
        TransactionResponse response = transactionService
                .processTransaction(request, "ritik@gmail.com");

        // Assert
        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal("9500.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("500.00"), toAccount.getBalance());
        verify(transactionRepository, times(1)).save(any());
    }

    // ── Test 2: Insufficient balance ──────────────────────────
    @Test
    void shouldRejectTransferWhenInsufficientBalance() {
        // Arrange
        TransactionRequest request = new TransactionRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setAmount(new BigDecimal("99999.00")); // more than balance
        request.setIdempotencyKey("unique-key-002");

        when(idempotencyKeyRepository.findByKeyAndExpiresAtAfter(
                any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdWithLock(fromAccountId))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId))
                .thenReturn(Optional.of(toAccount));
        when(userRepository.findByEmail("ritik@gmail.com"))
                .thenReturn(Optional.of(user));

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> transactionService.processTransaction(
                        request, "ritik@gmail.com")
        );

        assertTrue(exception.getMessage().contains("Insufficient balance"));
        verify(transactionRepository, never()).save(any());
    }

    // ── Test 3: Idempotency returns cached response ───────────
    @Test
    void shouldReturnCachedResponseForDuplicateRequest() throws Exception {
        // Arrange
        TransactionRequest request = new TransactionRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("duplicate-key-001");

        String cachedResponse = "{\"id\":\"" + UUID.randomUUID() + "\","
                + "\"amount\":500.00,"
                + "\"status\":\"SUCCESS\","
                + "\"type\":\"TRANSFER\","
                + "\"currency\":\"INR\"}";

        IdempotencyKey idempotencyKey = new IdempotencyKey(
                "duplicate-key-001", cachedResponse);

        when(idempotencyKeyRepository.findByKeyAndExpiresAtAfter(
                eq("duplicate-key-001"), any()))
                .thenReturn(Optional.of(idempotencyKey));

        // Act
        TransactionResponse response = transactionService
                .processTransaction(request, "ritik@gmail.com");

        // Assert — transaction should NOT be processed again
        assertNotNull(response);
        verify(accountRepository, never()).findByIdWithLock(any());
        verify(transactionRepository, never()).save(any());
    }

    // ── Test 4: Zero amount rejected ─────────────────────────
    @Test
    void shouldRejectZeroAmountTransaction() {
        TransactionRequest request = new TransactionRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setAmount(BigDecimal.ZERO);
        request.setIdempotencyKey("unique-key-003");

        when(idempotencyKeyRepository.findByKeyAndExpiresAtAfter(
                any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdWithLock(fromAccountId))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId))
                .thenReturn(Optional.of(toAccount));
        when(userRepository.findByEmail("ritik@gmail.com"))
                .thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> transactionService.processTransaction(
                        request, "ritik@gmail.com")
        );

        assertTrue(exception.getMessage()
                .contains("Amount must be greater than zero"));
        verify(transactionRepository, never()).save(any());
    }

    // ── Test 5: Access denied for wrong account owner ────────
    @Test
    void shouldRejectTransferFromAccountNotOwnedByUser() {
        // Different user owns the fromAccount
        User differentUser = new User();
        differentUser.setId(UUID.randomUUID());
        differentUser.setEmail("other@gmail.com");
        fromAccount.setUser(differentUser);

        TransactionRequest request = new TransactionRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("unique-key-004");

        when(idempotencyKeyRepository.findByKeyAndExpiresAtAfter(
                any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdWithLock(fromAccountId))
                .thenReturn(Optional.of(fromAccount));
        when(userRepository.findByEmail("ritik@gmail.com"))
                .thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> transactionService.processTransaction(
                        request, "ritik@gmail.com")
        );

        assertTrue(exception.getMessage().contains("Access denied"));
        verify(transactionRepository, never()).save(any());
    }
}