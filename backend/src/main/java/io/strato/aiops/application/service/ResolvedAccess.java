package io.strato.aiops.application.service;

import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.RoleBinding;
import io.strato.aiops.domain.identity.UserAccount;

import java.util.List;
import java.util.Set;

public record ResolvedAccess(UserAccount user, Set<Capability> capabilities, List<RoleBinding> bindings) {
}
