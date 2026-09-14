package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.application.ManagedApplication;

import java.util.List;
import java.util.UUID;

public interface ApplicationUseCase {

    ApplicationDeploymentResult deployDocker(DeployDockerApplicationCommand command, String actor, String requestId);

    ApplicationDeploymentResult deployHelm(DeployHelmApplicationCommand command, String actor, String requestId);

    List<ManagedApplication> listApplications();

    ManagedApplication getApplication(UUID applicationId);

    ApplicationStatusResult getApplicationStatus(UUID applicationId);

    UUID startApplicationSync(UUID applicationId, String actor, String requestId);

    UUID requestRestart(UUID applicationId, String actor, String requestId);

    ApplicationRollbackPreviewResult previewRollback(UUID applicationId, Integer targetRevision);

    UUID requestRollback(UUID applicationId, Integer targetRevision, String confirmText, String actor, String requestId);
}
