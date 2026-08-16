package com.extradict.fintechapi.notification;

import com.extradict.fintechapi.config.RedisConfig;
import com.extradict.fintechapi.event.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TransactionNotificationPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationPublisher.class);

    public TransactionNotificationPublisher(
            RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void publish(TransactionEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(
                    RedisConfig.TRANSACTION_CHANNEL, message);
            log.info("📤 Published transaction event: {}",
                    event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to publish transaction event: {}",
                    e.getMessage());
        }
    }
}