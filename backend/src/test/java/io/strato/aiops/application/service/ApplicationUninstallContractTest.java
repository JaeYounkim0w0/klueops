package io.strato.aiops.application.service;

import io.strato.aiops.adapter.out.persistence.JdbcApplicationLifecycleRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationUninstallContractTest {

    /** ApplicationUninstallContractTest의 applicationGraphDeletionIsAtomicAndRespectsForeignKeyOrder 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void applicationGraphDeletionIsAtomicAndRespectsForeignKeyOrder() throws Exception {
        var method = JdbcApplicationLifecycleRepositoryAdapter.class
                .getMethod("deleteApplicationGraph", UUID.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();

        String source = Files.readString(Path.of(
                "src/main/java/io/strato/aiops/adapter/out/persistence/JdbcApplicationLifecycleRepositoryAdapter.java"));
        int endpoints = source.indexOf("delete from application_endpoints where application_id=?", source.indexOf("deleteApplicationGraph"));
        int releases = source.indexOf("delete from application_releases where application_id=?", endpoints);
        int operations = source.indexOf("delete from release_operations where application_id=?", releases);
        int plans = source.indexOf("delete from deployment_plans where application_id=?", operations);
        int application = source.indexOf("delete from managed_applications where id=?", plans);

        // 자식 테이블을 먼저 정리해야 PostgreSQL과 H2 모두에서 FK 위반 없이 원자 삭제된다.
        assertThat(endpoints).isGreaterThanOrEqualTo(0);
        assertThat(releases).isGreaterThan(endpoints);
        assertThat(operations).isGreaterThan(releases);
        assertThat(plans).isGreaterThan(operations);
        assertThat(application).isGreaterThan(plans);
    }

    /** ApplicationUninstallContractTest의 successfulUninstallPurgesDetailsBeforeCompletingTheIndependentJob 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void successfulUninstallPurgesDetailsBeforeCompletingTheIndependentJob() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/strato/aiops/application/service/ApplicationDeliveryDeploymentService.java"));
        int uninstallBranch = source.indexOf("if (\"UNINSTALL\".equals(operationType)) {", source.indexOf("Instant completed"));
        int purge = source.indexOf("lifecycle.deleteApplicationGraph(application.id())", uninstallBranch);
        int complete = source.indexOf("job.markSucceeded(completed)", purge);
        int exit = source.indexOf("return;", complete);

        assertThat(uninstallBranch).isGreaterThanOrEqualTo(0);
        assertThat(purge).isGreaterThan(uninstallBranch);
        assertThat(complete).isGreaterThan(purge);
        assertThat(exit).isGreaterThan(complete);
    }

    /** ApplicationUninstallContractTest의 failedInstallCanStillBeDiscardedWhenHelmReleaseDoesNotExist 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void failedInstallCanStillBeDiscardedWhenHelmReleaseDoesNotExist() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/strato/aiops/application/service/HelmReleaseCommandRunner.java"));

        // 설치 실패 후 Release가 생성되지 않은 경우에도 사용자가 실패 항목을 제거할 수 있어야 한다.
        assertThat(source).contains("\"--ignore-not-found\", \"--wait\", \"--timeout\", \"5m\"");
    }

    /** ApplicationUninstallContractTest의 phaseTwoMigrationPurgesLegacyUninstalledApplicationGraphs 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void phaseTwoMigrationPurgesLegacyUninstalledApplicationGraphs() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V33__purge_uninstalled_applications.sql"));

        assertThat(migration).containsSubsequence(
                "delete from application_endpoints",
                "delete from application_releases",
                "delete from release_operations",
                "delete from deployment_plans",
                "delete from managed_applications");
        assertThat(migration).contains("where status = 'UNINSTALLED'");
    }
}
