package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;

public interface WatchSignalTriageUseCase {

    /** WatchSignalTriageUseCase의 ingestWatchSignal 처리 계약을 정의한다. */
    void ingestWatchSignal(WatchSignal signal);
}
