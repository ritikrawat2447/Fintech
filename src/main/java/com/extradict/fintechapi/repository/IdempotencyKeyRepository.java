package com.extradict.fintechapi.repository;

import com.extradict.fintechapi.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByKeyAndExpiresAtAfter(
        String key, LocalDateTime now);
}