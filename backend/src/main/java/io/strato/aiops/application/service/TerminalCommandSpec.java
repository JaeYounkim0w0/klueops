package io.strato.aiops.application.service;

import java.util.List;

record TerminalCommandSpec(String verb, String pod, String container, List<String> remoteCommand) {
}
