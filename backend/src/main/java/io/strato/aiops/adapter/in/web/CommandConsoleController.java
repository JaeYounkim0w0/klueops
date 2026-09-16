package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.dto.CommandCapabilityResponse;
import io.strato.aiops.adapter.in.web.dto.CommandExecutionRequest;
import io.strato.aiops.adapter.in.web.dto.CommandExecutionResponse;
import io.strato.aiops.adapter.in.web.dto.CommandFavoriteRequest;
import io.strato.aiops.adapter.in.web.dto.CommandFavoriteResponse;
import io.strato.aiops.adapter.in.web.dto.CommandValidationRequest;
import io.strato.aiops.adapter.in.web.dto.CommandValidationResponse;
import io.strato.aiops.adapter.in.web.dto.TerminalSessionResponse;
import io.strato.aiops.application.port.in.CommandConsoleUseCase;
import io.strato.aiops.application.port.in.CommandFavoriteUseCase;
import io.strato.aiops.application.port.in.CommandTerminalUseCase;
import io.strato.aiops.application.port.in.StartCommandExecutionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@Tag(name = "Kubernetes Console", description = "Cluster-scoped kubectl execution, streaming, history and favorites")
@RestController
@RequestMapping("/api/clusters/{clusterId}")
public class CommandConsoleController {
    private final CommandConsoleUseCase console;
    private final CommandFavoriteUseCase favorites;
    private final CommandTerminalUseCase terminals;
    private final int maximumOutputBytes;

    /** CommandConsoleController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandConsoleController(CommandConsoleUseCase console, CommandFavoriteUseCase favorites, CommandTerminalUseCase terminals,
                                    @Value("${aiops.command-console.maximum-output-bytes:1048576}") int maximumOutputBytes) {
        this.console = console;
        this.favorites = favorites;
        this.terminals = terminals;
        this.maximumOutputBytes = maximumOutputBytes;
    }

    /** CommandConsoleController의 capabilities 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get Kubernetes Console capabilities")
    @GetMapping("/command-capabilities")
    public CommandCapabilityResponse capabilities(@PathVariable UUID clusterId,
                                                  @RequestParam(required = false) String namespace) {
        var limits = console.limits();
        return new CommandCapabilityResponse(clusterId, namespace, console.runnerAvailable(), console.kubectlVersion(),
                console.executionBoundary(), "BACKEND_FABRIC8",
                console.metricsApiAvailable(clusterId),
                List.of("COMMAND", "STREAM", "TERMINAL", "MANIFEST", "HISTORY", "FAVORITES"), 16384, maximumOutputBytes,
                limits.userCommands(), limits.userTerminals(), limits.clusterCommands(), limits.clusterTerminals(),
                limits.userStartsPerMinute());
    }

    /** CommandConsoleController의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
    @Operation(summary = "Validate and classify a kubectl command")
    @PostMapping("/commands/validate")
    public CommandValidationResponse validate(@PathVariable UUID clusterId,
                                              @Valid @RequestBody CommandValidationRequest request) {
        return CommandValidationResponse.from(console.validate(clusterId, request.namespace(), request.command()));
    }

    /** CommandConsoleController의 execute 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "Start a kubectl command execution")
    @PostMapping("/command-executions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandExecutionResponse execute(@PathVariable UUID clusterId,
                                            @Valid @RequestBody CommandExecutionRequest request,
                                            HttpServletRequest servletRequest) {
        return CommandExecutionResponse.from(console.execute(new StartCommandExecutionCommand(clusterId,
                request.sourceAnalysisId(), request.namespace(), request.command(), request.manifest(), request.confirmed(), actor(servletRequest),
                requestId(servletRequest))));
    }

    /** CommandConsoleController의 createTerminal 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Create a single-use interactive kubectl exec or attach session")
    @PostMapping("/command-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalSessionResponse createTerminal(@PathVariable UUID clusterId,
                                                  @Valid @RequestBody CommandExecutionRequest request,
                                                  HttpServletRequest servletRequest) {
        return TerminalSessionResponse.from(terminals.create(new StartCommandExecutionCommand(clusterId,
                request.sourceAnalysisId(), request.namespace(), request.command(), null, request.confirmed(), actor(servletRequest),
                requestId(servletRequest))));
    }

    /** CommandConsoleController의 executions 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List recent kubectl executions")
    @GetMapping("/command-executions")
    public List<CommandExecutionResponse> executions(@PathVariable UUID clusterId,
                                                     @RequestParam(required = false) String namespace,
                                                     @RequestParam(defaultValue = "30") int limit) {
        return console.listExecutions(clusterId, namespace, limit).stream().map(CommandExecutionResponse::from).toList();
    }

    /** CommandConsoleController의 execution 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get a kubectl execution")
    @GetMapping("/command-executions/{executionId}")
    public CommandExecutionResponse execution(@PathVariable UUID clusterId, @PathVariable UUID executionId) {
        return CommandExecutionResponse.from(console.getExecution(clusterId, executionId));
    }

    /** CommandConsoleController의 stream 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Stream kubectl stdout, stderr and status events")
    @GetMapping(value = "/command-executions/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID clusterId, @PathVariable UUID executionId) {
        return console.stream(clusterId, executionId);
    }

    /** CommandConsoleController의 cancel 처리 조건의 충족 여부를 판단한다. */
    @Operation(summary = "Cancel a running kubectl execution")
    @PostMapping("/command-executions/{executionId}/cancel")
    public CommandExecutionResponse cancel(@PathVariable UUID clusterId, @PathVariable UUID executionId,
                                           HttpServletRequest request) {
        return CommandExecutionResponse.from(console.cancel(clusterId, executionId, actor(request), requestId(request)));
    }

    /** CommandConsoleController의 favorites 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List personal and shared command favorites")
    @GetMapping("/command-favorites")
    public List<CommandFavoriteResponse> favorites(@PathVariable UUID clusterId, HttpServletRequest request) {
        return favorites.list(clusterId, actor(request)).stream().map(CommandFavoriteResponse::from).toList();
    }

    /** CommandConsoleController의 createFavorite 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Create a command favorite")
    @PostMapping("/command-favorites")
    @ResponseStatus(HttpStatus.CREATED)
    public CommandFavoriteResponse createFavorite(@PathVariable UUID clusterId,
                                                  @Valid @RequestBody CommandFavoriteRequest body,
                                                  HttpServletRequest request) {
        return CommandFavoriteResponse.from(favorites.create(clusterId, body.name(), body.description(), body.command(),
                body.namespace(), body.shared(), body.sortOrder(), actor(request)));
    }

    /** CommandConsoleController의 updateFavorite 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update a command favorite")
    @PutMapping("/command-favorites/{favoriteId}")
    public CommandFavoriteResponse updateFavorite(@PathVariable UUID clusterId, @PathVariable UUID favoriteId,
                                                  @Valid @RequestBody CommandFavoriteRequest body,
                                                  HttpServletRequest request) {
        return CommandFavoriteResponse.from(favorites.update(clusterId, favoriteId, body.name(), body.description(),
                body.command(), body.namespace(), body.shared(), body.sortOrder(), actor(request)));
    }

    /** CommandConsoleController의 deleteFavorite 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Operation(summary = "Delete a command favorite")
    @DeleteMapping("/command-favorites/{favoriteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFavorite(@PathVariable UUID clusterId, @PathVariable UUID favoriteId,
                               HttpServletRequest request) {
        favorites.delete(clusterId, favoriteId, actor(request));
    }

    /** CommandConsoleController의 actor 처리에 필요한 업무 로직을 수행한다. */
    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "local-operator" : request.getUserPrincipal().getName();
    }

    /** CommandConsoleController의 requestId 처리에 필요한 업무 로직을 수행한다. */
    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestAttributes.REQUEST_ID);
        return value == null ? null : String.valueOf(value);
    }
}
