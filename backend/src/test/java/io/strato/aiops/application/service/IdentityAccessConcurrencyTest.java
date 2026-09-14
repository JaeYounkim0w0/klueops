package io.strato.aiops.application.service;

import io.strato.aiops.domain.identity.ExternalIdentity;
import io.strato.aiops.domain.identity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IdentityAccessConcurrencyTest {

    @Autowired
    private IdentityAccessService identityAccessService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void provisionsOneAccountWhenTheFirstAuthenticatedRequestsArriveConcurrently() throws Exception {
        String subject = "concurrent-first-login";
        ExternalIdentity identity = new ExternalIdentity(
                "https://idp.concurrent", subject, "concurrent-user", "Concurrent User",
                "concurrent@example.com", Set.of("aiops-viewers"));
        CountDownLatch start = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<UserAccount>> futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        return identityAccessService.provision(identity);
                    }))
                    .toList();
            start.countDown();

            Set<java.util.UUID> accountIds = new HashSet<>();
            for (Future<UserAccount> future : futures) {
                accountIds.add(future.get().id());
            }
            assertThat(accountIds).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from aiops_users where issuer = ? and subject = ?",
                Integer.class, identity.issuer(), identity.subject());
        assertThat(count).isEqualTo(1);
    }
}
