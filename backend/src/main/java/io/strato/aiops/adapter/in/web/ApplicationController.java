package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.dto.ApplicationDeploymentResponse;
import io.strato.aiops.adapter.in.web.dto.ApplicationRollbackPreviewResponse;
import io.strato.aiops.adapter.in.web.dto.ApplicationRollbackRequest;
import io.strato.aiops.adapter.in.web.dto.ApplicationResponse;
import io.strato.aiops.adapter.in.web.dto.ApplicationStatusResponse;
import io.strato.aiops.adapter.in.web.dto.DeployDockerApplicationRequest;
import io.strato.aiops.adapter.in.web.dto.DeployHelmApplicationRequest;
import io.strato.aiops.adapter.in.web.dto.StartJobResponse;
import io.strato.aiops.application.port.in.ApplicationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Applications", description = "Application deployment and status APIs")
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationUseCase applicationUseCase;

    public ApplicationController(ApplicationUseCase applicationUseCase) {
        this.applicationUseCase = applicationUseCase;
    }

    @Operation(summary = "Request Docker image application deployment")
    @PostMapping("/deploy/docker")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApplicationDeploymentResponse deployDocker(@Valid @RequestBody DeployDockerApplicationRequest request, HttpServletRequest servletRequest) {
        return ApplicationDeploymentResponse.from(applicationUseCase.deployDocker(request.toCommand(), actor(servletRequest), requestId(servletRequest)));
    }

    @Operation(summary = "Request Helm chart application deployment")
    @PostMapping("/deploy/helm")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApplicationDeploymentResponse deployHelm(@Valid @RequestBody DeployHelmApplicationRequest request, HttpServletRequest servletRequest) {
        return ApplicationDeploymentResponse.from(applicationUseCase.deployHelm(request.toCommand(), actor(servletRequest), requestId(servletRequest)));
    }

    @Operation(summary = "List applications")
    @GetMapping
    public List<ApplicationResponse> listApplications() {
        return applicationUseCase.listApplications().stream().map(ApplicationResponse::from).toList();
    }

    @Operation(summary = "Get application")
    @GetMapping("/{applicationId}")
    public ApplicationResponse getApplication(@PathVariable UUID applicationId) {
        return ApplicationResponse.from(applicationUseCase.getApplication(applicationId));
    }

    @Operation(summary = "Get application status")
    @GetMapping("/{applicationId}/status")
    public ApplicationStatusResponse getApplicationStatus(@PathVariable UUID applicationId) {
        return ApplicationStatusResponse.from(applicationUseCase.getApplicationStatus(applicationId));
    }

    @Operation(summary = "Request application synchronization")
    @PostMapping("/{applicationId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartJobResponse syncApplication(@PathVariable UUID applicationId, HttpServletRequest request) {
        return new StartJobResponse(applicationUseCase.startApplicationSync(applicationId, actor(request), requestId(request)));
    }

    @Operation(summary = "Request application restart")
    @PostMapping("/{applicationId}/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartJobResponse restartApplication(@PathVariable UUID applicationId, HttpServletRequest request) {
        return new StartJobResponse(applicationUseCase.requestRestart(applicationId, actor(request), requestId(request)));
    }

    @Operation(summary = "Request application rollback")
    @PostMapping("/{applicationId}/rollback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartJobResponse rollbackApplication(@PathVariable UUID applicationId,
                                                @Valid @RequestBody ApplicationRollbackRequest rollbackRequest,
                                                HttpServletRequest request) {
        return new StartJobResponse(applicationUseCase.requestRollback(applicationId, rollbackRequest.targetRevision(),
                rollbackRequest.confirmText(), actor(request), requestId(request)));
    }

    @Operation(summary = "Preview application rollback")
    @GetMapping("/{applicationId}/rollback/preview")
    public ApplicationRollbackPreviewResponse previewRollbackApplication(@PathVariable UUID applicationId,
                                                                        @RequestParam(required = false) Integer targetRevision) {
        return ApplicationRollbackPreviewResponse.from(applicationUseCase.previewRollback(applicationId, targetRevision));
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }
}
