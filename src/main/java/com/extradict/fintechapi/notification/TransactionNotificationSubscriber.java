package com.extradict.fintechapi.notification;

import com.extradict.fintechapi.event.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TransactionNotificationSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationPublisher.class);

    public TransactionNotificationSubscriber() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

   @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());

            // Body comes as: ""{\\"key\\":\\"value\\"}"" 
            // Need to unescape twice
            // Step 1: parse outer JSON string to get inner string
            String unescaped = objectMapper.readValue(body, String.class);

            // Step 2: parse inner string to get TransactionEvent
            TransactionEvent event = objectMapper
                    .readValue(unescaped, TransactionEvent.class);

            sendEmail(event);
            sendSms(event);

        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage());
        }
    }

    private void sendEmail(TransactionEvent event) {
        // Simulated — real implementation would use
        // JavaMailSender or SendGrid API
        log.info("📧 EMAIL → To: {} | Transaction: {} | Amount: {} {}",
                event.getUserEmail(),
                event.getTransactionId(),
                event.getAmount(),
                event.getCurrency());
        log.info("📧 EMAIL content: Your transfer of {} {} was {}",
                event.getAmount(),
                event.getCurrency(),
                event.getStatus());
    }

    private void sendSms(TransactionEvent event) {
        // Simulated — real implementation would use
        // Twilio or AWS SNS
        log.info("📱 SMS → To: {} | Amount: {} {} | Status: {}",
                event.getUserEmail(),
                event.getAmount(),
                event.getCurrency(),
                event.getStatus());
    }
}