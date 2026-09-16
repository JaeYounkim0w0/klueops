package io.strato.aiops.application.service;

import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class ApplicationOperationConcurrencyPolicy {
    private static final Set<ApplicationStatus> ACTIVE_OPERATION_STATUSES = EnumSet.of(
            ApplicationStatus.DEPLOY_REQUESTED,
            ApplicationStatus.DEPLOYING,
            ApplicationStatus.UPGRADING,
            ApplicationStatus.ROLLING_BACK,
            ApplicationStatus.UNINSTALLING
    );

    /** ApplicationOperationConcurrencyPolicy의 requireOperationAvailable 처리 입력과 현재 상태의 유효성을 검증한다. */
    public void requireOperationAvailable(ManagedApplication application) {
        if (ACTIVE_OPERATION_STATUSES.contains(application.status())) {
            // 동일 Helm Release의 병렬 mutation은 Helm storage와 상태 이력을 경합시키므로 명시적으로 거부한다.
            throw new ApplicationOperationConflictException(
                    "Another lifecycle operation is already active for application '" + application.name() + "'");
        }
    }
}
