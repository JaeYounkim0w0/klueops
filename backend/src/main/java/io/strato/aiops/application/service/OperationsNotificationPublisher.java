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

    /** OperationsNotificationPublisher 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationsNotificationPublisher(OperationsRepositoryPort operationsRepository) {
        this.operationsRepository = operationsRepository;
    }

    /** OperationsNotificationPublisher의 publish 처리 결과를 지정된 대상에 전달한다. */
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

    /** OperationsNotificationPublisher의 dedupKey 처리에 필요한 업무 로직을 수행한다. */
    static String dedupKey(String type, String sourceKey, int suppressMinutes, Instant now) {
        long bucketSeconds = Math.max(60, suppressMinutes * 60L);
        long bucket = now.getEpochSecond() / bucketSeconds;
        return sha256(type + "|" + sourceKey + "|" + bucket);
    }

    /** OperationsNotificationPublisher의 cleanText 처리에 필요한 업무 로직을 수행한다. */
    static String cleanText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^\\s,]+", "$1=***");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    /** OperationsNotificationPublisher의 sha256 처리에 필요한 업무 로직을 수행한다. */
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
