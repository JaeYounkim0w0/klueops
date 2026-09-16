package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.UserAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepositoryPort {
    /** UserAccountRepositoryPort의 findByIssuerAndSubject 처리 결과를 조회해 반환한다. */
    Optional<UserAccount> findByIssuerAndSubject(String issuer, String subject);
    /** UserAccountRepositoryPort의 lockProvisioning 처리 계약을 정의한다. */
    void lockProvisioning(String issuer, String subject);
    /** UserAccountRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<UserAccount> findById(UUID id);
    /** UserAccountRepositoryPort의 findAll 처리 결과를 조회해 반환한다. */
    List<UserAccount> findAll();
    /** UserAccountRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    UserAccount save(UserAccount user);
}
