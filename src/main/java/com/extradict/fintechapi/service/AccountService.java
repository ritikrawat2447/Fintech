package com.extradict.fintechapi.service;

import com.extradict.fintechapi.dto.AccountRequest;
import com.extradict.fintechapi.dto.AccountResponse;
import com.extradict.fintechapi.entity.Account;
import com.extradict.fintechapi.entity.User;
import com.extradict.fintechapi.repository.AccountRepository;
import com.extradict.fintechapi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // ── Create account for logged in user ─────────────────────
    public AccountResponse createAccount(String userEmail,
                                         AccountRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Account account = new Account();
        account.setUser(user);
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency(
            request.getCurrency() != null ? request.getCurrency() : "INR"
        );

        Account saved = accountRepository.save(account);
        return mapToResponse(saved);
    }

    // ── List all accounts for logged in user ──────────────────
    public List<AccountResponse> getMyAccounts(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return accountRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Get balance for specific account ──────────────────────
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID accountId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Compare using user ID instead of lazy loading user from account
        if (!account.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return account.getBalance();
    }

    // ── Map entity to response ────────────────────────────────
    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setUserId(account.getUser().getId());
        response.setBalance(account.getBalance());
        response.setCurrency(account.getCurrency());
        response.setStatus(account.getStatus());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }
}