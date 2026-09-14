package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.command.CommandFavorite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_favorites")
class CommandFavoriteEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID clusterId;
    @Column(nullable = false)
    private String ownerUserId;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(length = 300)
    private String description;
    @Column(name = "command_text", nullable = false, columnDefinition = "text")
    private String command;
    private String namespace;
    @Column(nullable = false)
    private boolean shared;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected CommandFavoriteEntity() {
    }

    static CommandFavoriteEntity fromDomain(CommandFavorite value) {
        CommandFavoriteEntity entity = new CommandFavoriteEntity();
        entity.id = value.id();
        entity.clusterId = value.clusterId();
        entity.ownerUserId = value.ownerUserId();
        entity.name = value.name();
        entity.description = value.description();
        entity.command = value.command();
        entity.namespace = value.namespace();
        entity.shared = value.shared();
        entity.sortOrder = value.sortOrder();
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        return entity;
    }

    CommandFavorite toDomain() {
        return new CommandFavorite(id, clusterId, ownerUserId, name, description, command, namespace, shared,
                sortOrder, createdAt, updatedAt);
    }
}

