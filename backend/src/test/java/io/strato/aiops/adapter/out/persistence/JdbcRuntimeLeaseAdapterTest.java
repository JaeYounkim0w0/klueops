package io.strato.aiops.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("local")
class JdbcRuntimeLeaseAdapterTest {

    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    /** JdbcRuntimeLeaseAdapterTest의 onlyOneOwnerHoldsALiveLeaseAndReleaseAllowsTakeover 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @Test
    void onlyOneOwnerHoldsALiveLeaseAndReleaseAllowsTakeover() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        var tx = new TransactionTemplate(transactionManager);
        var first = new JdbcRuntimeLeaseAdapter(jdbc, tx, "first");
        var second = new JdbcRuntimeLeaseAdapter(jdbc, tx, "second");

        assertThat(first.acquireOrRenew("watch:cluster-1", Duration.ofMinutes(1))).isTrue();
        assertThat(second.acquireOrRenew("watch:cluster-1", Duration.ofMinutes(1))).isFalse();
        assertThat(first.acquireOrRenew("watch:cluster-1", Duration.ofMinutes(1))).isTrue();

        first.release("watch:cluster-1");
        assertThat(second.acquireOrRenew("watch:cluster-1", Duration.ofMinutes(1))).isTrue();
    }
}
