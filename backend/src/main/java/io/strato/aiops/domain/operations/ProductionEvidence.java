package io.strato.aiops.domain.operations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ProductionEvidence {

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+\\-/]+=*");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)((?:password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\\s*[:=]\\s*)[^\\s,;]+"
    );

    /** ProductionEvidence 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private ProductionEvidence() {
    }

    /** ProductionEvidence의 sha256 처리에 필요한 업무 로직을 수행한다. */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** ProductionEvidence의 sanitize 처리에 필요한 업무 로직을 수행한다. */
    public static String sanitize(String value) {
        if (value == null || value.isBlank()) return value;
        return ASSIGNMENT.matcher(BEARER.matcher(value).replaceAll("$1***")).replaceAll("$1***");
    }

    public enum State {
        NOT_RUN, RUNNING, PASSED, FAILED, BLOCKED, EXPIRED
    }

    public record Run(UUID id, String releaseName, String environment, State state,
                      String triggeredBy, Instant startedAt, Instant completedAt,
                      Instant expiresAt, List<Check> checks) {

        /** Run의 start 처리에 필요한 업무 로직을 수행한다. */
        public static Run start(UUID id, String releaseName, String environment,
                                String triggeredBy, Instant startedAt) {
            return new Run(id, releaseName, environment, State.RUNNING, triggeredBy,
                    startedAt, null, null, List.of());
        }

        /** Run의 complete 처리에 필요한 업무 로직을 수행한다. */
        public Run complete(State target, Instant completedAt, List<Check> checks) {
            if (state != State.RUNNING) throw new IllegalStateException("Only a running evidence run can complete");
            if (target == State.RUNNING || target == State.NOT_RUN || target == State.EXPIRED) {
                throw new IllegalArgumentException("Invalid terminal evidence state: " + target);
            }
            return new Run(id, releaseName, environment, target, triggeredBy, startedAt,
                    completedAt, completedAt.plusSeconds(30L * 24 * 60 * 60), List.copyOf(checks));
        }
    }

    public record Check(UUID id, UUID runId, String category, String code, State state,
                        String title, String detail, String observedValue, String action,
                        long durationMs, Instant checkedAt, List<Artifact> artifacts) {
    }

    public record Artifact(UUID id, UUID checkId, String fileName, String mediaType,
                           String checksum, long sizeBytes, String reference, Instant createdAt) {

        /** Artifact의 metadata 처리에 필요한 업무 로직을 수행한다. */
        public static Artifact metadata(UUID checkId, String fileName, String mediaType,
                                        String content, String reference, Instant createdAt) {
            String safe = sanitize(content == null ? "" : content);
            return new Artifact(UUID.randomUUID(), checkId, fileName, mediaType,
                    sha256(safe), safe.getBytes(StandardCharsets.UTF_8).length, reference, createdAt);
        }
    }
}
