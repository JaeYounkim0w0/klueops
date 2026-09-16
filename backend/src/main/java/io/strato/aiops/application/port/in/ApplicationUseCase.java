package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.application.ManagedApplication;

import java.util.List;
import java.util.UUID;

public interface ApplicationUseCase {

    /** ApplicationUseCase의 deployDocker 처리 계약을 정의한다. */
    ApplicationDeploymentResult deployDocker(DeployDockerApplicationCommand command, String actor, String requestId);

    /** ApplicationUseCase의 deployHelm 처리 계약을 정의한다. */
    ApplicationDeploymentResult deployHelm(DeployHelmApplicationCommand command, String actor, String requestId);

    /** ApplicationUseCase의 listApplications 처리 결과를 조회해 반환한다. */
    List<ManagedApplication> listApplications();

    /** ApplicationUseCase의 getApplication 처리 결과를 조회해 반환한다. */
    ManagedApplication getApplication(UUID applicationId);

    /** ApplicationUseCase의 getApplicationStatus 처리 결과를 조회해 반환한다. */
    ApplicationStatusResult getApplicationStatus(UUID applicationId);

    /** ApplicationUseCase의 startApplicationSync 처리 계약을 정의한다. */
    UUID startApplicationSync(UUID applicationId, String actor, String requestId);

    /** ApplicationUseCase의 requestRestart 처리 계약을 정의한다. */
    UUID requestRestart(UUID applicationId, String actor, String requestId);

    /** ApplicationUseCase의 previewRollback 처리 계약을 정의한다. */
    ApplicationRollbackPreviewResult previewRollback(UUID applicationId, Integer targetRevision);

    /** ApplicationUseCase의 requestRollback 처리 계약을 정의한다. */
    UUID requestRollback(UUID applicationId, Integer targetRevision, String confirmText, String actor, String requestId);
}
