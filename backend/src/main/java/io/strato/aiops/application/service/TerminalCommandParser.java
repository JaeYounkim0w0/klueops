package io.strato.aiops.application.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class TerminalCommandParser {
    private static final Set<String> OPTIONS_WITH_VALUE = Set.of(
            "-c", "--container", "-n", "--namespace", "--pod-running-timeout"
    );

    /** TerminalCommandParser의 parse 처리 데이터를 필요한 표현으로 변환한다. */
    TerminalCommandSpec parse(List<String> arguments) {
        int verbIndex = findVerb(arguments);
        String verb = arguments.get(verbIndex);
        String pod = null;
        String container = null;
        List<String> remote = new ArrayList<>();
        boolean afterSeparator = false;
        for (int index = verbIndex + 1; index < arguments.size(); index++) {
            String value = arguments.get(index);
            if (afterSeparator) {
                remote.add(value);
                continue;
            }
            if ("--".equals(value)) {
                afterSeparator = true;
                continue;
            }
            if (value.startsWith("--container=")) {
                container = value.substring("--container=".length());
                continue;
            }
            if (("-c".equals(value) || "--container".equals(value)) && index + 1 < arguments.size()) {
                container = arguments.get(++index);
                continue;
            }
            if (OPTIONS_WITH_VALUE.contains(value) && index + 1 < arguments.size()) {
                index++;
                continue;
            }
            if (value.startsWith("--namespace=") || value.startsWith("--pod-running-timeout=") || value.startsWith("-")) {
                continue;
            }
            if (pod == null) {
                pod = value;
            } else if ("exec".equals(verb)) {
                remote.add(value);
            }
        }
        if (pod == null || pod.isBlank()) throw new IllegalArgumentException("interactive command requires a pod name");
        if ("exec".equals(verb) && remote.isEmpty()) throw new IllegalArgumentException("kubectl exec requires a command after the pod name");
        return new TerminalCommandSpec(verb, pod, container, List.copyOf(remote));
    }

    /** TerminalCommandParser의 findVerb 처리 결과를 조회해 반환한다. */
    private int findVerb(List<String> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            String value = arguments.get(index);
            if ("exec".equals(value) || "attach".equals(value)) return index;
        }
        throw new IllegalArgumentException("interactive terminal supports kubectl exec and attach");
    }
}
