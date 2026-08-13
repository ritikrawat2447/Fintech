package com.extradict.fintechapi.controller;

import com.extradict.fintechapi.dto.AccountRequest;
import com.extradict.fintechapi.dto.AccountResponse;
import com.extradict.fintechapi.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // POST /accounts → create account
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @RequestBody AccountRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        AccountResponse response = accountService.createAccount(
                userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /accounts → list my accounts
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<AccountResponse> accounts = accountService
                .getMyAccounts(userDetails.getUsername());
        return ResponseEntity.ok(accounts);
    }

    // GET /accounts/{id}/balance → get balance
    @GetMapping("/{id}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        BigDecimal balance = accountService.getBalance(
                id, userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
                "accountId", id,
                "balance", balance,
                "currency", "INR"
        ));
    }
}