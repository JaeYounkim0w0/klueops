package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.operations.OperationsModels.Notification;
import io.strato.aiops.domain.operations.OperationsModels.OperationSettings;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Component
public class OperationsNotificationPublisher {

    private final OperationsRepositoryPort operationsRepository;

    public OperationsNotificationPublisher(OperationsRepositoryPort operationsRepository) {
        this.operationsRepository = operationsRepository;
    }

    public Notification publish(String type,
                                String severity,
                                String title,
                                String message,
                                String targetPath,
                                String sourceKey) {
        OperationSettings settings = operationsRepository.getOperationSettings();
        Instant now = Instant.now();
        String dedupKey = dedupKey(type, sourceKey, settings.notificationSuppressMinutes(), now);
        Notification current = operationsRepository.findNotificationByDedupKey(dedupKey).orElse(null);
        return operationsRepository.saveNotification(current == null
                ? new Notification(UUID.randomUUID(), dedupKey, type, severity, cleanText(title, 500),
                cleanText(message, 2000), cleanText(targetPath, 1000), false, 1, now, now)
                : new Notification(current.id(), current.dedupKey(), type, severity, cleanText(title, 500),
                cleanText(message, 2000), cleanText(targetPath, 1000), false,
                current.occurrenceCount() + 1, current.createdAt(), now));
    }

    static String dedupKey(String type, String sourceKey, int suppressMinutes, Instant now) {
        long bucketSeconds = Math.max(60, suppressMinutes * 60L);
        long bucket = now.getEpochSecond() / bucketSeconds;
        return sha256(type + "|" + sourceKey + "|" + bucket);
    }

    static String cleanText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^\\s,]+", "$1=***");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
