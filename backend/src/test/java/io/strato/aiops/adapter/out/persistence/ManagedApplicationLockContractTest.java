package io.strato.aiops.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedApplicationLockContractTest {

    /** ManagedApplicationLockContractTest의 lifecycleMutationUsesDatabaseWriteLock 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void lifecycleMutationUsesDatabaseWriteLock() throws Exception {
        var method = ManagedApplicationJpaRepository.class.getMethod("findByIdForUpdate", UUID.class);
        Lock lock = method.getAnnotation(Lock.class);

        // 여러 Backend replica에서도 동일 Application mutation은 DB row lock으로 직렬화한다.
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
