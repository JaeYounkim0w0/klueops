package io.strato.aiops.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.adapter.in.web.dto.ClusterResourceLogResponse;
import io.strato.aiops.adapter.in.web.dto.ClusterResourceLogTargetsResponse;
import io.strato.aiops.application.port.in.GetClusterResourceLogsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

@Tag(name = "Cluster Resource Logs", description = "Live Kubernetes workload log APIs")
@RestController
@RequestMapping("/api/clusters/{clusterId}/resources/{resourceType}/{resourceName}/logs")
public class ClusterResourceLogController {

    private final GetClusterResourceLogsUseCase logUseCase;
    private final ObjectMapper objectMapper;
    private final TaskScheduler scheduler;
    private final long heartbeatMs;

    public ClusterResourceLogController(GetClusterResourceLogsUseCase logUseCase,
                                        ObjectMapper objectMapper,
                                        @Qualifier("resourceLogScheduler") TaskScheduler scheduler,
                                        @Value("${aiops.cluster-logs.heartbeat-ms:15000}") long heartbeatMs) {
        this.logUseCase = logUseCase;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.heartbeatMs = Math.max(1, heartbeatMs);
    }

    @Operation(summary = "List Pods and containers related to a Kubernetes resource")
    @GetMapping("/targets")
    public ClusterResourceLogTargetsResponse getTargets(@PathVariable UUID clusterId,
                                                        @PathVariable String resourceType,
                                                        @PathVariable String resourceName,
                                                        @RequestParam String namespace) {
        return ClusterResourceLogTargetsResponse.from(
                logUseCase.getTargets(clusterId, namespace, resourceType, resourceName));
    }

    @Operation(summary = "Get the latest N log lines for a resource Pod and container")
    @GetMapping
    public ClusterResourceLogResponse getRecentLogs(@PathVariable UUID clusterId,
                                                    @PathVariable String resourceType,
                                                    @PathVariable String resourceName,
                                                    @RequestParam String namespace,
                                                    @RequestParam String podName,
                                                    @RequestParam String containerName,
                                                    @RequestParam(defaultValue = "100") int tailLines,
                                                    @RequestParam(defaultValue = "false") boolean previous) {
        return ClusterResourceLogResponse.from(logUseCase.getRecentLogs(clusterId, namespace, resourceType,
                resourceName, podName, containerName, tailLines, previous));
    }

    @Operation(summary = "Stream live logs for a resource Pod and container")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamLogs(@PathVariable UUID clusterId,
                                                            @PathVariable String resourceType,
                                                            @PathVariable String resourceName,
                                                            @RequestParam String namespace,
                                                            @RequestParam String podName,
                                                            @RequestParam String containerName,
                                                            @RequestParam(defaultValue = "100") int tailLines) {
        StreamingResponseBody body = outputStream -> {
            ClusterResourceLogSseWriter writer = new ClusterResourceLogSseWriter(outputStream, objectMapper);
            writer.writeJson("meta", Map.of(
                    "podName", podName,
                    "containerName", containerName,
                    "tailLines", tailLines,
                    "startedAt", Instant.now()
            ));
            ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(writer::writeHeartbeat,
                    Instant.now().plusMillis(heartbeatMs), Duration.ofMillis(heartbeatMs));
            try {
                var result = logUseCase.streamLogs(clusterId, namespace, resourceType, resourceName, podName,
                        containerName, tailLines, line -> writer.writeJson("log", line), writer::failed);
                writer.writeJson("done", result);
            } catch (RuntimeException exception) {
                if (!writer.failed()) {
                    writer.writeError(exception.getMessage());
                }
            } finally {
                if (heartbeat != null) {
                    heartbeat.cancel(false);
                }
            }
        };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }
}
