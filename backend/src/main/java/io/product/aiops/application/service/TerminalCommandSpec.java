package io.product.aiops.application.service;

import java.util.List;

record TerminalCommandSpec(String verb, String pod, String container, List<String> remoteCommand) {
}
