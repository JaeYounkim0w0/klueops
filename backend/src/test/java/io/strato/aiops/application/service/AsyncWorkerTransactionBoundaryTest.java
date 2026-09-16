package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncWorkerTransactionBoundaryTest {

    /** AsyncWorkerTransactionBoundaryTest의 clusterSyncDoesNotHoldADatabaseTransactionDuringKubernetesCalls 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void clusterSyncDoesNotHoldADatabaseTransactionDuringKubernetesCalls() throws Exception {
        assertNotSupported(ClusterSyncWorker.class.getMethod("runClusterSync", UUID.class));
    }

    /** AsyncWorkerTransactionBoundaryTest의 analysisDoesNotHoldADatabaseTransactionDuringKubernetesAndLlmCalls 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void analysisDoesNotHoldADatabaseTransactionDuringKubernetesAndLlmCalls() throws Exception {
        assertNotSupported(AnalysisApplicationService.class.getMethod("runAnalysisJob", UUID.class));
    }

    /** AsyncWorkerTransactionBoundaryTest의 applicationDeliveryDoesNotHoldDatabaseTransactionDuringHelmCalls 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void applicationDeliveryDoesNotHoldDatabaseTransactionDuringHelmCalls() throws Exception {
        assertNotSupported(ApplicationDeliveryDeploymentService.class.getMethod("executeInstall",
                UUID.class, UUID.class, UUID.class, UUID.class, UUID.class));
        assertNotSupported(ApplicationDeliveryDeploymentService.class.getMethod("executeRollback",
                UUID.class, UUID.class, int.class, UUID.class, UUID.class, String.class));
        assertNotSupported(ApplicationDeliveryDeploymentService.class.getMethod("executeUninstall",
                UUID.class, UUID.class, UUID.class, UUID.class, String.class));
    }

    /** AsyncWorkerTransactionBoundaryTest의 assertNotSupported 처리에 필요한 업무 로직을 수행한다. */
    private void assertNotSupported(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
