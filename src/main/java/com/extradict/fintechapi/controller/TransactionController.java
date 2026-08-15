package com.extradict.fintechapi.controller;

import com.extradict.fintechapi.dto.TransactionRequest;
import com.extradict.fintechapi.dto.TransactionResponse;
import com.extradict.fintechapi.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // POST /transactions → process payment
    @PostMapping
    public ResponseEntity<TransactionResponse> processTransaction(
            @RequestBody TransactionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        TransactionResponse response = transactionService
                .processTransaction(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /transactions/{id} → get transaction details
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        TransactionResponse response = transactionService
                .getTransaction(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // GET /transactions → list my transactions
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<TransactionResponse> transactions = transactionService
                .getMyTransactions(userDetails.getUsername());
        return ResponseEntity.ok(transactions);
    }
}