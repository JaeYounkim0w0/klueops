package io.product.aiops.application.service;

import io.product.aiops.domain.identity.Capability;
import io.product.aiops.domain.identity.RoleBinding;
import io.product.aiops.domain.identity.UserAccount;

import java.util.List;
import java.util.Set;

public record ResolvedAccess(UserAccount user, Set<Capability> capabilities, List<RoleBinding> bindings) {
}
