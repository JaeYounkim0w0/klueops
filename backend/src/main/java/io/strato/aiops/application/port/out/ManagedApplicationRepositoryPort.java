package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.application.ManagedApplication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagedApplicationRepositoryPort {

    ManagedApplication save(ManagedApplication application);

    Optional<ManagedApplication> findById(UUID applicationId);

    List<ManagedApplication> findRecent(int limit);
}
