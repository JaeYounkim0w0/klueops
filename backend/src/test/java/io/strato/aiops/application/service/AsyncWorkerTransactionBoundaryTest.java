package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncWorkerTransactionBoundaryTest {

    @Test
    void clusterSyncDoesNotHoldADatabaseTransactionDuringKubernetesCalls() throws Exception {
        assertNotSupported(ClusterSyncWorker.class.getMethod("runClusterSync", UUID.class));
    }

    @Test
    void analysisDoesNotHoldADatabaseTransactionDuringKubernetesAndLlmCalls() throws Exception {
        assertNotSupported(AnalysisApplicationService.class.getMethod("runAnalysisJob", UUID.class));
    }

    private void assertNotSupported(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
