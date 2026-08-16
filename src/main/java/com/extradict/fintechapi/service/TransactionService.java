package com.extradict.fintechapi.service;

import com.extradict.fintechapi.dto.TransactionRequest;
import com.extradict.fintechapi.dto.TransactionResponse;
import com.extradict.fintechapi.entity.Account;
import com.extradict.fintechapi.entity.IdempotencyKey;
import com.extradict.fintechapi.entity.Transaction;
import com.extradict.fintechapi.entity.User;
import com.extradict.fintechapi.enums.TransactionStatus;
import com.extradict.fintechapi.enums.TransactionType;
import com.extradict.fintechapi.repository.AccountRepository;
import com.extradict.fintechapi.repository.IdempotencyKeyRepository;
import com.extradict.fintechapi.repository.TransactionRepository;
import com.extradict.fintechapi.repository.UserRepository;
import com.extradict.fintechapi.notification.TransactionNotificationPublisher;
import com.extradict.fintechapi.event.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {
        private final TransactionRepository transactionRepository;
        private final AccountRepository accountRepository;
        private final IdempotencyKeyRepository idempotencyKeyRepository;
        private final UserRepository userRepository;
        private final ObjectMapper objectMapper;
        private final TransactionNotificationPublisher notificationPublisher;

        public TransactionService(TransactionRepository transactionRepository,
                                AccountRepository accountRepository,
                                IdempotencyKeyRepository idempotencyKeyRepository,
                                UserRepository userRepository,
                                TransactionNotificationPublisher notificationPublisher) {
                this.transactionRepository = transactionRepository;
                this.accountRepository = accountRepository;
                this.idempotencyKeyRepository = idempotencyKeyRepository;
                this.userRepository = userRepository;
                this.notificationPublisher = notificationPublisher;
                this.objectMapper = new ObjectMapper();
                this.objectMapper.registerModule(new JavaTimeModule());
        }

        @Transactional
        public TransactionResponse processTransaction(TransactionRequest request,
                                                        String userEmail) {
                // ── Step 1: Idempotency check ─────────────────────────
                // Have we seen this request before?
                if (request.getIdempotencyKey() != null) {
                var existing = idempotencyKeyRepository
                        .findByKeyAndExpiresAtAfter(
                                request.getIdempotencyKey(),
                                LocalDateTime.now()
                        );

                if (existing.isPresent()) {
                        // Return cached response — no duplicate charge
                        try {
                        return objectMapper.readValue(
                                existing.get().getResponse(),
                                TransactionResponse.class
                        );
                        } catch (Exception e) {
                        throw new RuntimeException("Failed to parse cached response");
                        }
                }
                }

                // ── Step 2: Validate sender account ──────────────────
                // Lock the row — SELECT FOR UPDATE
                // Prevents two requests debiting same account simultaneously
                Account fromAccount = accountRepository
                        .findByIdWithLock(request.getFromAccountId())
                        .orElseThrow(() -> new RuntimeException(
                                "Sender account not found"));

                // Verify account belongs to logged in user
                User user = userRepository.findByEmail(userEmail)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                if (!fromAccount.getUser().getId().equals(user.getId())) {
                throw new RuntimeException("Access denied to sender account");
                }

                // ── Step 3: Validate receiver account ────────────────
                Account toAccount = accountRepository
                        .findByIdWithLock(request.getToAccountId())
                        .orElseThrow(() -> new RuntimeException(
                                "Receiver account not found"));

                // ── Step 4: Check sufficient balance ─────────────────
                if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
                throw new RuntimeException("Insufficient balance. Available: "
                        + fromAccount.getBalance());
                }

                // ── Step 5: Check amount is positive ─────────────────
                if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Amount must be greater than zero");
                }

                // ── Step 6: Move the money ────────────────────────────
                // Debit sender
                fromAccount.setBalance(
                        fromAccount.getBalance().subtract(request.getAmount()));

                // Credit receiver
                toAccount.setBalance(
                        toAccount.getBalance().add(request.getAmount()));

                // Save both accounts
                accountRepository.save(fromAccount);
                accountRepository.save(toAccount);

                // ── Step 7: Create transaction record ─────────────────
                Transaction transaction = new Transaction();
                transaction.setIdempotencyKey(
                        request.getIdempotencyKey() != null
                                ? request.getIdempotencyKey()
                                : UUID.randomUUID().toString()
                );
                transaction.setFromAccount(fromAccount);
                transaction.setToAccount(toAccount);
                transaction.setAmount(request.getAmount());
                transaction.setCurrency(
                        request.getCurrency() != null ? request.getCurrency() : "INR");
                transaction.setType(TransactionType.TRANSFER);
                transaction.setStatus(TransactionStatus.SUCCESS);
                transaction.setDescription(request.getDescription());
                transaction.setProcessedAt(LocalDateTime.now());

                Transaction saved = transactionRepository.save(transaction);
                TransactionResponse response = mapToResponse(saved);

                // ── Step 8: Cache idempotency key ─────────────────────
                if (request.getIdempotencyKey() != null) {
                try {
                        String responseJson = objectMapper.writeValueAsString(response);
                        idempotencyKeyRepository.save(
                                new IdempotencyKey(
                                        request.getIdempotencyKey(),
                                        responseJson
                                )
                        );
                } catch (JsonProcessingException e) {
                        // Non-critical — log and continue
                        System.err.println("Failed to cache idempotency key: "
                                + e.getMessage());
                }
                }

                // ── Step 9: Publish notification event ───────────────
                TransactionEvent event = new TransactionEvent(
                        saved.getId(),
                        fromAccount.getId(),
                        toAccount.getId(),
                        saved.getAmount(),
                        saved.getCurrency(),
                        saved.getStatus().name(),
                        userEmail
                );
                notificationPublisher.publish(event);

                return response;
        }

        // ── Get single transaction ────────────────────────────────
        @Transactional(readOnly = true)
        public TransactionResponse getTransaction(UUID transactionId,
                                                String userEmail) {
                Transaction transaction = transactionRepository
                        .findById(transactionId)
                        .orElseThrow(() -> new RuntimeException(
                                "Transaction not found"));

                return mapToResponse(transaction);
        }

        // ── List all transactions for user ────────────────────────
        @Transactional(readOnly = true)
        public List<TransactionResponse> getMyTransactions(String userEmail) {
                User user = userRepository.findByEmail(userEmail)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                List<Account> accounts = accountRepository.findByUserId(user.getId());

                return accounts.stream()
                        .flatMap(account -> transactionRepository
                                .findByFromAccountIdOrToAccountId(
                                        account.getId(), account.getId())
                                .stream())
                        .map(this::mapToResponse)
                        .distinct()
                        .collect(Collectors.toList());
        }

        // ── Map entity to response ────────────────────────────────
        private TransactionResponse mapToResponse(Transaction t) {
                TransactionResponse response = new TransactionResponse();
                response.setId(t.getId());
                response.setFromAccountId(
                        t.getFromAccount() != null
                                ? t.getFromAccount().getId() : null);
                response.setToAccountId(
                        t.getToAccount() != null
                                ? t.getToAccount().getId() : null);
                response.setAmount(t.getAmount());
                response.setCurrency(t.getCurrency());
                response.setType(t.getType());
                response.setStatus(t.getStatus());
                response.setDescription(t.getDescription());
                response.setIdempotencyKey(t.getIdempotencyKey());
                response.setCreatedAt(t.getCreatedAt());
                response.setProcessedAt(t.getProcessedAt());
                return response;
        }
}