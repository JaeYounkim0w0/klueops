package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.CommandValidationResult;
import io.strato.aiops.domain.command.CommandSafety;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class KubectlCommandTokenizer {
    private static final int MAXIMUM_COMMAND_LENGTH = 16_384;
    private static final Set<String> CREDENTIAL_OPTIONS = Set.of(
            "--kubeconfig", "--server", "--token", "--certificate-authority",
            "--client-certificate", "--client-key", "--username", "--password", "--as", "--as-group"
    );
    private static final Set<String> READ_ONLY = Set.of(
            "get", "describe", "explain", "api-resources", "api-versions", "version", "cluster-info",
            "auth", "top", "logs", "wait", "events", "diff"
    );
    private static final Set<String> DESTRUCTIVE = Set.of("delete", "drain");
    private static final Set<String> INTERACTIVE = Set.of("exec", "attach", "port-forward", "proxy", "cp", "debug");
    private static final Set<String> GLOBAL_OPTIONS_WITH_VALUE = Set.of(
            "--namespace", "-n", "--request-timeout", "--cache-dir", "--kuberc", "--profile", "--profile-output"
    );

    /** KubectlCommandTokenizer의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
    public CommandValidationResult validate(String command, String defaultNamespace) {
        List<String> arguments = tokenize(command);
        if (!arguments.isEmpty() && "kubectl".equals(arguments.get(0))) arguments = new ArrayList<>(arguments.subList(1, arguments.size()));
        if (arguments.isEmpty()) throw new IllegalArgumentException("kubectl command is required");
        rejectCredentialOverrides(arguments);
        rejectContextSwitch(arguments);
        String verb = firstCommand(arguments);
        CommandSafety safety = safety(verb, arguments);
        String namespace = namespace(arguments, defaultNamespace);
        boolean interactive = INTERACTIVE.contains(verb) && isInteractive(arguments);
        List<String> warnings = new ArrayList<>();
        if (safety == CommandSafety.DESTRUCTIVE) warnings.add("삭제 또는 서비스 영향이 가능한 명령입니다.");
        if (interactive) warnings.add("대화형 명령은 전용 터미널 세션에서 실행됩니다.");
        return new CommandValidationResult("kubectl " + join(arguments), List.copyOf(arguments), namespace, safety,
                safety == CommandSafety.CHANGE || safety == CommandSafety.DESTRUCTIVE
                        || safety == CommandSafety.PRIVILEGED_INTERACTIVE,
                interactive, targetSummary(verb, arguments, namespace), List.copyOf(warnings));
    }

    /** KubectlCommandTokenizer의 tokenize 처리 데이터를 필요한 표현으로 변환한다. */
    List<String> tokenize(String command) {
        if (command == null || command.isBlank()) throw new IllegalArgumentException("command is required");
        if (command.length() > MAXIMUM_COMMAND_LENGTH) throw new IllegalArgumentException("command exceeds 16384 characters");
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < command.length(); index++) {
            char value = command.charAt(index);
            if (value == '\0' || value == '\n' || value == '\r') throw new IllegalArgumentException("multiple commands are not supported");
            if (escaped) {
                current.append(value);
                escaped = false;
                continue;
            }
            if (value == '\\' && quote != '\'') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (value == quote) quote = 0;
                else current.append(value);
                continue;
            }
            if (value == '\'' || value == '"') {
                quote = value;
                continue;
            }
            if (Character.isWhitespace(value)) {
                flush(tokens, current);
                continue;
            }
            if (value == ';' || value == '|' || value == '>' || value == '<' || value == '`'
                    || (value == '$' && index + 1 < command.length() && command.charAt(index + 1) == '(')) {
                throw new IllegalArgumentException("shell operators are not supported; run one kubectl command at a time");
            }
            current.append(value);
        }
        if (escaped || quote != 0) throw new IllegalArgumentException("command contains an unfinished quote or escape");
        flush(tokens, current);
        return tokens;
    }

    /** KubectlCommandTokenizer의 rejectCredentialOverrides 처리에 필요한 업무 로직을 수행한다. */
    private void rejectCredentialOverrides(List<String> arguments) {
        for (String argument : arguments) {
            String option = argument.contains("=") ? argument.substring(0, argument.indexOf('=')) : argument;
            if (CREDENTIAL_OPTIONS.contains(option)) {
                throw new IllegalArgumentException(option + " is managed by the platform and cannot be overridden");
            }
        }
    }

    /** KubectlCommandTokenizer의 rejectContextSwitch 처리에 필요한 업무 로직을 수행한다. */
    private void rejectContextSwitch(List<String> arguments) {
        String verb = firstCommand(arguments);
        if ("config".equals(verb)) {
            throw new IllegalArgumentException("kubectl config cannot change the cluster context managed by this page");
        }
        for (String argument : arguments) {
            if (argument.equals("--context") || argument.startsWith("--context=") || argument.equals("--cluster")
                    || argument.startsWith("--cluster=") || argument.equals("--user") || argument.startsWith("--user=")) {
                throw new IllegalArgumentException("cluster context overrides are not supported");
            }
        }
    }

    /** KubectlCommandTokenizer의 safety 처리에 필요한 업무 로직을 수행한다. */
    private CommandSafety safety(String verb, List<String> arguments) {
        if (DESTRUCTIVE.contains(verb)) return CommandSafety.DESTRUCTIVE;
        if (INTERACTIVE.contains(verb)) return CommandSafety.PRIVILEGED_INTERACTIVE;
        if (READ_ONLY.contains(verb)) return "logs".equals(verb) ? CommandSafety.DIAGNOSE : CommandSafety.READ_ONLY;
        if ("rollout".equals(verb) && arguments.stream().anyMatch(value -> value.equals("status") || value.equals("history"))) {
            return CommandSafety.READ_ONLY;
        }
        return CommandSafety.CHANGE;
    }

    /** KubectlCommandTokenizer의 isInteractive 처리 조건의 충족 여부를 판단한다. */
    private boolean isInteractive(List<String> arguments) {
        return arguments.stream().anyMatch(value -> value.equals("-i") || value.equals("-t")
                || value.equals("--stdin") || value.equals("--tty") || value.startsWith("--stdin=")
                || value.startsWith("--tty=") || value.equals("-it") || value.equals("-ti"));
    }

    /** KubectlCommandTokenizer의 firstCommand 처리에 필요한 업무 로직을 수행한다. */
    private String firstCommand(List<String> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            String value = arguments.get(index);
            if (value.startsWith("-")) {
                if (!value.contains("=") && GLOBAL_OPTIONS_WITH_VALUE.contains(value) && index + 1 < arguments.size()) index++;
                continue;
            }
            return value.toLowerCase(Locale.ROOT);
        }
        return "";
    }

    /** KubectlCommandTokenizer의 namespace 처리에 필요한 업무 로직을 수행한다. */
    private String namespace(List<String> arguments, String defaultNamespace) {
        for (int index = 0; index < arguments.size(); index++) {
            String value = arguments.get(index);
            if ((value.equals("-n") || value.equals("--namespace")) && index + 1 < arguments.size()) return arguments.get(index + 1);
            if (value.startsWith("--namespace=")) return value.substring("--namespace=".length());
        }
        return defaultNamespace;
    }

    /** KubectlCommandTokenizer의 targetSummary 처리에 필요한 업무 로직을 수행한다. */
    private String targetSummary(String verb, List<String> arguments, String namespace) {
        String resource = arguments.stream().dropWhile(value -> !value.equalsIgnoreCase(verb)).skip(1)
                .filter(value -> !value.startsWith("-")).findFirst().orElse("cluster");
        return verb + " " + resource + (namespace == null || namespace.isBlank() ? " · cluster scope" : " · " + namespace);
    }

    /** KubectlCommandTokenizer의 join 처리에 필요한 업무 로직을 수행한다. */
    private String join(List<String> values) {
        return values.stream().map(this::quoteIfNeeded).reduce((left, right) -> left + " " + right).orElse("");
    }

    /** KubectlCommandTokenizer의 quoteIfNeeded 처리에 필요한 업무 로직을 수행한다. */
    private String quoteIfNeeded(String value) {
        return value.chars().anyMatch(Character::isWhitespace) ? "\"" + value.replace("\"", "\\\"") + "\"" : value;
    }

    /** KubectlCommandTokenizer의 flush 처리에 필요한 업무 로직을 수행한다. */
    private void flush(List<String> tokens, StringBuilder current) {
        if (current.length() == 0) return;
        tokens.add(current.toString());
        current.setLength(0);
    }
}
