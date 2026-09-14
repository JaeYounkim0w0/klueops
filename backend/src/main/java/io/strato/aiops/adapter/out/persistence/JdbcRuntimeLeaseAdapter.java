package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.RuntimeLeasePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class JdbcRuntimeLeaseAdapter implements RuntimeLeasePort {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String ownerId;

    public JdbcRuntimeLeaseAdapter(JdbcTemplate jdbcTemplate,
                                   TransactionTemplate transactionTemplate,
                                   @Value("${aiops.runtime.instance-id:${HOSTNAME:local}}") String instanceId) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.ownerId = instanceId + ":" + UUID.randomUUID();
    }

    @Override
    public boolean acquireOrRenew(String leaseKey, Duration ttl) {
        if (leaseKey == null || leaseKey.isBlank()) throw new IllegalArgumentException("leaseKey must not be blank");
        Duration effectiveTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
        Boolean acquired = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(effectiveTtl);
            int updated = jdbcTemplate.update("""
                    INSERT INTO runtime_leases (lease_key, owner_id, acquired_at, expires_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (lease_key) DO UPDATE
                       SET owner_id = EXCLUDED.owner_id,
                           acquired_at = EXCLUDED.acquired_at,
                           expires_at = EXCLUDED.expires_at
                     WHERE runtime_leases.owner_id = EXCLUDED.owner_id
                        OR runtime_leases.expires_at <= EXCLUDED.acquired_at
                    """, leaseKey, ownerId, Timestamp.from(now), Timestamp.from(expiresAt));
            return updated == 1;
        });
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String leaseKey) {
        jdbcTemplate.update("DELETE FROM runtime_leases WHERE lease_key = ? AND owner_id = ?", leaseKey, ownerId);
    }
}
