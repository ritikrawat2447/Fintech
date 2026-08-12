package com.extradict.fintechapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(nullable = false, length = 255)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

    public IdempotencyKey() {}

    public IdempotencyKey(String key, String response) {
        this.key = key;
        this.response = response;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}