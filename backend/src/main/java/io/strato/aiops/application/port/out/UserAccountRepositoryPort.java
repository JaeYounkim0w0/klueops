package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.UserAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepositoryPort {
    Optional<UserAccount> findByIssuerAndSubject(String issuer, String subject);
    void lockProvisioning(String issuer, String subject);
    Optional<UserAccount> findById(UUID id);
    List<UserAccount> findAll();
    UserAccount save(UserAccount user);
}
