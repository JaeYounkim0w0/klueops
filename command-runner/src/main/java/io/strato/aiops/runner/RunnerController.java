package io.strato.aiops.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
final class RunnerController {
    private final RunnerProcessExecutor executor;
    private final RunnerReplayGuard replayGuard;
    private final ObjectMapper objectMapper;
    private final String kubectlPath;

    RunnerController(RunnerProcessExecutor executor, RunnerReplayGuard replayGuard, ObjectMapper objectMapper,
                     @Value("${runner.kubectl-path:kubectl}") String kubectlPath) {
        this.executor = executor;
        this.replayGuard = replayGuard;
        this.objectMapper = objectMapper;
        this.kubectlPath = kubectlPath;
    }

    @PostMapping(value = "/executions", produces = "application/x-ndjson")
    StreamingResponseBody execute(@Valid @RequestBody RunnerRequest request) {
        if (!replayGuard.accept(request.executionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "execution id was already accepted");
        }
        return output -> executor.execute(request, event -> {
            synchronized (output) {
                output.write(objectMapper.writeValueAsBytes(event));
                output.write('\n');
                output.flush();
            }
        });
    }

    @DeleteMapping("/executions/{executionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable UUID executionId) {
        if (!executor.cancel(executionId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @GetMapping(value = "/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> capabilities() {
        return Map.of("kubectlVersion", executor.clientVersion(kubectlPath), "streaming", true, "isolated", true);
    }
}
