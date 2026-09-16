package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.UserAccountRepositoryPort;
import io.strato.aiops.domain.identity.UserAccount;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class UserAccountPersistenceAdapter implements UserAccountRepositoryPort {
    private static final int PROVISIONING_LOCK_BUCKETS = 32;

    private final UserAccountJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    /** UserAccountPersistenceAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    UserAccountPersistenceAdapter(UserAccountJpaRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** UserAccountPersistenceAdapter의 findByIssuerAndSubject 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<UserAccount> findByIssuerAndSubject(String issuer, String subject) {
        return repository.findByIssuerAndSubject(issuer, subject).map(UserAccountEntity::toDomain);
    }

    /** UserAccountPersistenceAdapter의 lockProvisioning 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void lockProvisioning(String issuer, String subject) {
        int bucket = Math.floorMod(java.util.Objects.hash(issuer, subject), PROVISIONING_LOCK_BUCKETS);
        jdbcTemplate.queryForObject(
                "select bucket from aiops_identity_provision_locks where bucket = ? for update",
                Integer.class,
                bucket
        );
    }

    /** UserAccountPersistenceAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<UserAccount> findById(UUID id) {
        return repository.findById(id).map(UserAccountEntity::toDomain);
    }

    /** UserAccountPersistenceAdapter의 findAll 처리 결과를 조회해 반환한다. */
    @Override
    public List<UserAccount> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream().map(UserAccountEntity::toDomain).toList();
    }

    /** UserAccountPersistenceAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public UserAccount save(UserAccount user) {
        return repository.save(UserAccountEntity.fromDomain(user)).toDomain();
    }
}
