package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.command.CommandFavorite;

import java.util.List;
import java.util.UUID;

public interface CommandFavoriteUseCase {
    /** CommandFavoriteUseCase의 list 처리 결과를 조회해 반환한다. */
    List<CommandFavorite> list(UUID clusterId, String actor);
    /** CommandFavoriteUseCase의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    CommandFavorite create(UUID clusterId, String name, String description, String command, String namespace,
                           boolean shared, int sortOrder, String actor);
    /** CommandFavoriteUseCase의 update 처리 대상의 상태를 갱신한다. */
    CommandFavorite update(UUID clusterId, UUID favoriteId, String name, String description, String command,
                           String namespace, boolean shared, int sortOrder, String actor);
    /** CommandFavoriteUseCase의 delete 처리 대상과 관련 상태를 안전하게 정리한다. */
    void delete(UUID clusterId, UUID favoriteId, String actor);
}

