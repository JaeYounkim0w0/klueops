package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterCredentialTransactionPolicyTest {

    /** ClusterCredentialTransactionPolicyTest의 credentialRevealUsesWritableTransactionForAuditLog 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void credentialRevealUsesWritableTransactionForAuditLog() throws NoSuchMethodException {
        Transactional transactional = ClusterApplicationService.class
                .getMethod("getClusterCredential", java.util.UUID.class, boolean.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        // 원문 조회 감사 로그가 flush될 수 있도록 쓰기 가능한 트랜잭션을 유지한다.
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }
}
