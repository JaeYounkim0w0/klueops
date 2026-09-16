package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.operations.OperationsModels.PolicyDefinition;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.PolicyResult;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsPolicyEvaluatorTest {

    private final OperationsPolicyEvaluator evaluator = new OperationsPolicyEvaluator(new ObjectMapper());

    /** OperationsPolicyEvaluatorTest의 evaluatesWorkloadAvailabilityFromReplicaEvidence 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void evaluatesWorkloadAvailabilityFromReplicaEvidence() {
        Cluster cluster = cluster();
        KubernetesResourceSnapshot deployment = snapshot(cluster, "default", "Deployment", "api",
                "{\"desiredReplicas\":2,\"availableReplicas\":1}");

        List<PolicyEvaluation> evaluations = evaluator.evaluate(cluster, List.of(deployment),
                List.of(policy("WORKLOAD_AVAILABILITY")));

        assertThat(evaluations).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.policyId()).isEqualTo("WORKLOAD_AVAILABILITY");
            assertThat(evaluation.result()).isEqualTo(PolicyResult.FAIL);
            assertThat(evaluation.evidence()).contains("available=1", "desired=2");
        });
    }

    /** OperationsPolicyEvaluatorTest의 detectsServiceTargetPortMismatchAgainstSelectedWorkload 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void detectsServiceTargetPortMismatchAgainstSelectedWorkload() {
        Cluster cluster = cluster();
        KubernetesResourceSnapshot deployment = snapshot(cluster, "nginx", "Deployment", "web", """
                {"desiredReplicas":2,"availableReplicas":2,"templateLabels":{"app":"web"},
                 "containers":[{"ports":[{"name":"http","containerPort":8080}]}]}
                """);
        KubernetesResourceSnapshot service = snapshot(cluster, "nginx", "Service", "web", """
                {"selector":{"app":"web"},"ports":[{"port":80,"targetPort":9090}]}
                """);

        List<PolicyEvaluation> evaluations = evaluator.evaluate(cluster, List.of(deployment, service),
                List.of(policy("PORT_MISMATCH")));

        assertThat(evaluations).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.resourceKind()).isEqualTo("Service");
            assertThat(evaluation.result()).isEqualTo(PolicyResult.FAIL);
        });
    }

    /** OperationsPolicyEvaluatorTest의 warnsWhenNamespaceGovernanceResourcesAreMissing 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void warnsWhenNamespaceGovernanceResourcesAreMissing() {
        Cluster cluster = cluster();
        KubernetesResourceSnapshot pod = snapshot(cluster, "payments", "Pod", "worker", "{\"phase\":\"Running\"}");

        List<PolicyEvaluation> evaluations = evaluator.evaluate(cluster, List.of(pod), List.of(
                policy("NETWORK_POLICY_MISSING"),
                policy("RESOURCE_QUOTA_MISSING"),
                policy("LIMIT_RANGE_MISSING")
        ));

        assertThat(evaluations).extracting(PolicyEvaluation::policyId)
                .containsExactlyInAnyOrder("NETWORK_POLICY_MISSING", "RESOURCE_QUOTA_MISSING", "LIMIT_RANGE_MISSING");
        assertThat(evaluations).allSatisfy(evaluation -> {
            assertThat(evaluation.namespace()).isEqualTo("payments");
            assertThat(evaluation.resourceKind()).isEqualTo("Namespace");
            assertThat(evaluation.result()).isEqualTo(PolicyResult.WARN);
        });
    }

    /** OperationsPolicyEvaluatorTest의 cluster 처리에 필요한 업무 로직을 수행한다. */
    private Cluster cluster() {
        return Cluster.register("test-cluster", "policy test", ClusterEnvironment.DEV,
                ClusterProvider.KIND, "local", "test");
    }

    /** OperationsPolicyEvaluatorTest의 policy 처리에 필요한 업무 로직을 수행한다. */
    private PolicyDefinition policy(String id) {
        return new PolicyDefinition(id, id, "test policy", "TEST", "MEDIUM", true);
    }

    /** OperationsPolicyEvaluatorTest의 snapshot 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesResourceSnapshot snapshot(Cluster cluster, String namespace, String kind, String name,
                                                String summaryJson) {
        return new KubernetesResourceSnapshot(UUID.randomUUID(), cluster.id(), UUID.randomUUID(), namespace,
                kind, name, UUID.randomUUID().toString(), "ACTIVE", summaryJson, null, false, Instant.now());
    }
}
