package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.identity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aiops_users")
class UserAccountEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 500)
    private String issuer;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private String displayName;
    @Column(length = 320)
    private String email;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private Instant firstSeenAt;
    @Column(nullable = false)
    private Instant lastLoginAt;
    @Column(nullable = false)
    private Instant updatedAt;

    /** UserAccountEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected UserAccountEntity() {
    }

    /** UserAccountEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private UserAccountEntity(UserAccount user) {
        this.id = user.id();
        this.issuer = user.issuer();
        this.subject = user.subject();
        this.username = user.username();
        this.displayName = user.displayName();
        this.email = user.email();
        this.active = user.active();
        this.firstSeenAt = user.firstSeenAt();
        this.lastLoginAt = user.lastLoginAt();
        this.updatedAt = user.updatedAt();
    }

    /** UserAccountEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static UserAccountEntity fromDomain(UserAccount user) {
        return new UserAccountEntity(user);
    }

    /** UserAccountEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    UserAccount toDomain() {
        return new UserAccount(id, issuer, subject, username, displayName, email, active, firstSeenAt, lastLoginAt, updatedAt);
    }
}
