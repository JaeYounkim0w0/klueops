package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;

public interface WatchSignalTriageUseCase {

    void ingestWatchSignal(WatchSignal signal);
}
