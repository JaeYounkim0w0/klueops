package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.SensitiveDataMaskingPort;
import io.strato.aiops.application.port.out.CommandExecutionRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentDetail;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.command.CommandExecution;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class IncidentReportExportService {

    private final OperationsControlPlaneService operationsService;
    private final AuditLogRepositoryPort auditRepository;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMaskingPort secretMasker;
    private final MeterRegistry meterRegistry;
    private final CommandExecutionRepositoryPort commandExecutions;

    public IncidentReportExportService(OperationsControlPlaneService operationsService,
                                       AuditLogRepositoryPort auditRepository,
                                       ObjectMapper objectMapper,
                                       SensitiveDataMaskingPort secretMasker,
                                       MeterRegistry meterRegistry,
                                       CommandExecutionRepositoryPort commandExecutions) {
        this.operationsService = operationsService;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.secretMasker = secretMasker;
        this.meterRegistry = meterRegistry;
        this.commandExecutions = commandExecutions;
    }

    public ExportedReport export(UUID incidentId, String format, String actor, String requestId) {
        IncidentDetail detail = operationsService.getIncident(incidentId);
        String normalized = format == null ? "markdown" : format.trim().toLowerCase();
        ReportWriter writer;
        String mediaType;
        String extension;
        Instant generatedAt = Instant.now();
        switch (normalized) {
            case "json" -> {
                writer = output -> writeJson(detail, linkedCommands(detail), generatedAt, output);
                mediaType = "application/json";
                extension = "json";
            }
            case "zip" -> {
                writer = output -> writeZip(detail, linkedCommands(detail), generatedAt, output);
                mediaType = "application/zip";
                extension = "zip";
            }
            case "markdown", "md" -> {
                writer = output -> output.write(markdown(detail, linkedCommands(detail)).getBytes(StandardCharsets.UTF_8));
                mediaType = "text/markdown;charset=UTF-8";
                extension = "md";
            }
            default -> throw new IllegalArgumentException("Supported report formats are markdown, json, and zip");
        }
        DigestResult digest = digest(writer);
        auditRepository.save(AuditLog.create("INCIDENT_REPORT_EXPORTED", "INCIDENT", incidentId.toString(), actor, requestId));
        meterRegistry.counter("aiops.incident.report.exports", "format", normalized).increment();
        DistributionSummary.builder("aiops.incident.report.bytes").tag("format", normalized)
                .register(meterRegistry).record(digest.bytes());
        return new ExportedReport("incident-" + incidentId + "." + extension, mediaType, writer,
                digest.sha256(), generatedAt);
    }

    String markdown(IncidentDetail detail) {
        return markdown(detail, linkedCommands(detail));
    }

    private String markdown(IncidentDetail detail, List<CommandExecution> commands) {
        var incident = detail.incident();
        StringBuilder report = new StringBuilder();
        report.append("# Incident Report\n\n")
                .append("- Incident: `").append(incident.id()).append("`\n")
                .append("- Cluster: ").append(value(incident.clusterName())).append(" (`").append(incident.clusterId()).append("`)\n")
                .append("- Namespace: ").append(value(incident.namespace())).append("\n")
                .append("- Resource: ").append(value(incident.resourceKind())).append('/').append(value(incident.resourceName())).append("\n")
                .append("- Severity / State: ").append(value(incident.severity())).append(" / ").append(incident.state()).append("\n")
                .append("- First / Last detected: ").append(incident.firstDetectedAt()).append(" / ").append(incident.lastDetectedAt()).append("\n\n")
                .append("## Summary\n\n").append(value(incident.summary())).append("\n\n")
                .append("## Recommended Next Action\n\n").append(value(incident.nextAction())).append("\n\n")
                .append("## Evidence\n\n");
        for (IncidentEvidence evidence : detail.evidence()) {
            report.append("- **").append(evidence.factual() ? "FACT" : "INFERENCE").append(" · ")
                    .append(value(evidence.evidenceType())).append("**: ").append(value(evidence.summary()))
                    .append(" (`").append(value(evidence.sourceRef())).append("`, ").append(evidence.occurredAt()).append(")\n");
        }
        report.append("\n## Timeline\n\n");
        for (IncidentActivity activity : detail.timeline()) {
            report.append("- ").append(activity.createdAt()).append(" · **").append(value(activity.activityType())).append("**")
                    .append(" · ").append(value(activity.actor())).append(" · ").append(value(activity.note())).append("\n");
        }
        report.append("\n## Linked Command Verifications\n\n");
        if (commands.isEmpty()) report.append("- No command execution is linked to the source analysis.\n");
        for (CommandExecution command : commands) {
            report.append("- ").append(command.createdAt()).append(" · `").append(value(command.command())).append("`")
                    .append(" · ").append(command.status()).append(" / ").append(command.verificationStatus())
                    .append(" · exit ").append(command.exitCode() == null ? "-" : command.exitCode())
                    .append(" · before `").append(hash(command.beforeSnapshot())).append("`")
                    .append(" · after `").append(hash(command.afterSnapshot())).append("`\n");
        }
        report.append("\n## Confidence And Missing Evidence\n\n")
                .append("- Score: ").append(detail.intelligence().confidence().score()).append(" / 100\n")
                .append("- Level: ").append(detail.intelligence().confidence().level()).append("\n");
        detail.intelligence().confidence().missingEvidence().forEach(item -> report.append("- Missing: ").append(item).append("\n"));
        report.append("\n---\nGenerated from persisted incident evidence. Secret values and live container logs are not included.\n");
        return report.toString();
    }

    private void writeJson(IncidentDetail detail, List<CommandExecution> commands, Instant generatedAt, OutputStream output) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new NonClosingOutputStream(output), Map.of(
                    "schemaVersion", "incident-report.v2",
                    "generatedAt", generatedAt,
                    "privacy", Map.of("secretsIncluded", false, "liveLogsIncluded", false),
                    "incident", sanitized(detail),
                    "commandVerifications", commandEvidence(commands)
            ));
    }

    private void writeZip(IncidentDetail detail, List<CommandExecution> commands, Instant generatedAt, OutputStream output) throws IOException {
        ReportWriter markdownWriter = target -> target.write(markdown(detail, commands).getBytes(StandardCharsets.UTF_8));
        ReportWriter jsonWriter = target -> writeJson(detail, commands, generatedAt, target);
        ReportWriter commandsWriter = target -> target.write(commandsCsv(detail).getBytes(StandardCharsets.UTF_8));
        ReportWriter executionWriter = target -> target.write(executionsCsv(commands).getBytes(StandardCharsets.UTF_8));
        String manifest = digest(markdownWriter).sha256() + "  report.md\n"
                + digest(jsonWriter).sha256() + "  report.json\n"
                + digest(commandsWriter).sha256() + "  commands.csv\n"
                + digest(executionWriter).sha256() + "  command-executions.csv\n";
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            add(zip, "report.md", markdownWriter);
            add(zip, "report.json", jsonWriter);
            add(zip, "commands.csv", commandsWriter);
            add(zip, "command-executions.csv", executionWriter);
            add(zip, "manifest.sha256", target -> target.write(manifest.getBytes(StandardCharsets.UTF_8)));
            zip.finish();
        }
    }

    private void add(ZipOutputStream zip, String name, ReportWriter writer) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        writer.writeTo(zip);
        zip.closeEntry();
    }

    private DigestResult digest(ReportWriter writer) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            CountingOutputStream output = new CountingOutputStream(
                    new java.security.DigestOutputStream(OutputStream.nullOutputStream(), messageDigest));
            writer.writeTo(output);
            return new DigestResult(HexFormat.of().formatHex(messageDigest.digest()), output.count());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank() ? "-"
                : secretMasker.maskText(value.toString()).replaceAll("[\\r\\n]+", " ");
    }

    private String commandsCsv(IncidentDetail detail) {
        String header = "order,title,purpose,command,expectedSignal,safetyLevel,destructive\n";
        return header + detail.intelligence().verificationPlan().stream()
                .map(step -> String.join(",",
                        Integer.toString(step.order()), csv(step.title()), csv(step.purpose()), csv(step.command()),
                        csv(step.expectedSignal()), csv(step.safetyLevel()), Boolean.toString(step.destructive())))
                .collect(Collectors.joining("\n"));
    }

    private List<Map<String, Object>> commandEvidence(List<CommandExecution> commands) {
        return commands.stream().map(command -> Map.<String, Object>ofEntries(
                Map.entry("executionId", command.id().toString()),
                Map.entry("command", value(command.command())),
                Map.entry("status", command.status().name()),
                Map.entry("verificationStatus", command.verificationStatus().name()),
                Map.entry("exitCode", command.exitCode() == null ? -1 : command.exitCode()),
                Map.entry("actor", value(command.createdBy())),
                Map.entry("createdAt", command.createdAt().toString()),
                Map.entry("beforeSha256", hash(command.beforeSnapshot())),
                Map.entry("afterSha256", hash(command.afterSnapshot()))
        )).toList();
    }

    private String executionsCsv(List<CommandExecution> commands) {
        String header = "executionId,createdAt,actor,command,status,exitCode,verificationStatus,beforeSha256,afterSha256\n";
        return header + commands.stream().map(command -> String.join(",", command.id().toString(),
                        command.createdAt().toString(), csv(command.createdBy()), csv(command.command()),
                        command.status().name(), command.exitCode() == null ? "" : command.exitCode().toString(),
                        command.verificationStatus().name(), hash(command.beforeSnapshot()), hash(command.afterSnapshot())))
                .collect(Collectors.joining("\n"));
    }

    private List<CommandExecution> linkedCommands(IncidentDetail detail) {
        UUID analysisId = detail.incident().sourceAnalysisId();
        return analysisId == null ? List.of() : commandExecutions.findBySourceAnalysisId(analysisId, 200);
    }

    private String hash(String value) {
        if (value == null || value.isBlank()) return "-";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String csv(Object value) {
        String text = secretMasker.maskText(value == null ? "" : value.toString()).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private JsonNode sanitized(Object value) {
        JsonNode root = objectMapper.valueToTree(value);
        sanitizeNode(root);
        return root;
    }

    private void sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> {
                if (secretMasker.isSensitiveKey(entry.getKey())) object.put(entry.getKey(), "***");
                else if (entry.getValue().isTextual()) object.put(entry.getKey(), secretMasker.maskText(entry.getValue().asText()));
                else sanitizeNode(entry.getValue());
            });
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::sanitizeNode);
        }
    }

    @FunctionalInterface
    public interface ReportWriter {
        void writeTo(OutputStream output) throws IOException;
    }

    private record DigestResult(String sha256, long bytes) {
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        private CountingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            out.write(value, offset, length);
            count += length;
        }

        private long count() {
            return count;
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        private NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    public record ExportedReport(String filename, String mediaType, ReportWriter writer, String sha256,
                                 Instant generatedAt) {
        public void writeTo(OutputStream output) throws IOException {
            writer.writeTo(output);
        }
    }
}
