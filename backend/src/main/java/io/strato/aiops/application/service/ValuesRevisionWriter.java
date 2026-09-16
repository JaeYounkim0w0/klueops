package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.applicationdelivery.ValuesRevision;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class ValuesRevisionWriter {
    private final ApplicationDeliveryRepositoryPort repository;
    private final SecretCryptoPort secretCrypto;
    private final Clock clock;

    /** ValuesRevisionWriter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ValuesRevisionWriter(ApplicationDeliveryRepositoryPort repository, SecretCryptoPort secretCrypto, Clock clock) {
        this.repository = repository;
        this.secretCrypto = secretCrypto;
        this.clock = clock;
    }

    /** ValuesRevisionWriter의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Transactional
    public ValuesRevision create(UUID tenantId, UUID profileId, String valuesYaml, String actor) {
        repository.findProfile(tenantId, profileId).orElseThrow();
        int revision = repository.nextRevision(profileId);
        ValuesRevision saved = new ValuesRevision(UUID.randomUUID(), profileId, revision,
                secretCrypto.encrypt(valuesYaml), sha256(valuesYaml), revision == 1 ? null : revision - 1,
                actor, clock.instant());
        return repository.saveRevision(saved);
    }

    /** ValuesRevisionWriter의 sha256 처리에 필요한 업무 로직을 수행한다. */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
