package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.port.in.ClusterReadinessReport;
import io.strato.aiops.application.port.in.ClusterReadinessUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Cluster Readiness", description = "Evidence-based cluster capability, credential, and upgrade readiness APIs")
@RestController
@RequestMapping("/api/clusters/{clusterId}")
public class ClusterReadinessController {

    private final ClusterReadinessUseCase useCase;

    public ClusterReadinessController(ClusterReadinessUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "Get combined cluster operational readiness")
    @GetMapping("/readiness")
    public ClusterReadinessReport readiness(@PathVariable UUID clusterId,
                                            @RequestParam(required = false) String namespace,
                                            @RequestParam(required = false) String targetVersion,
                                            @RequestParam(defaultValue = "false") boolean refresh) {
        return useCase.getReadiness(clusterId, namespace, targetVersion, refresh);
    }

    @Operation(summary = "Get Kubernetes credential capability matrix")
    @GetMapping("/capabilities")
    public ClusterReadinessReport.CapabilityMatrix capabilities(@PathVariable UUID clusterId,
                                                                 @RequestParam(required = false) String namespace,
                                                                 @RequestParam(defaultValue = "false") boolean refresh) {
        return useCase.getReadiness(clusterId, namespace, null, refresh).capabilities();
    }

    @Operation(summary = "Get cluster credential health without exposing secrets")
    @GetMapping("/credential-health")
    public ClusterReadinessReport.CredentialHealth credentialHealth(@PathVariable UUID clusterId,
                                                                     @RequestParam(defaultValue = "false") boolean refresh) {
        return useCase.getReadiness(clusterId, null, null, refresh).credential();
    }

    @Operation(summary = "Get Kubernetes upgrade readiness")
    @GetMapping("/upgrade-readiness")
    public ClusterReadinessReport.UpgradeReadiness upgradeReadiness(@PathVariable UUID clusterId,
                                                                     @RequestParam(required = false) String targetVersion,
                                                                     @RequestParam(defaultValue = "false") boolean refresh) {
        return useCase.getReadiness(clusterId, null, targetVersion, refresh).upgrade();
    }
}
