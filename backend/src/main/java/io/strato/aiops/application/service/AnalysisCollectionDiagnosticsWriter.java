package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.springframework.stereotype.Component;

@Component
final class AnalysisCollectionDiagnosticsWriter {

    void write(ObjectNode root, KubernetesNamespaceDiagnostics diagnostics) {
        if (diagnostics.collectionStages().isEmpty()) {
            return;
        }
        ObjectNode analysisDiagnostics = root.with("analysisDiagnostics");
        ObjectNode collection = analysisDiagnostics.putObject("collection");
        long successful = diagnostics.collectionStages().stream()
                .filter(stage -> "SUCCEEDED".equals(stage.status())).count();
        long failed = diagnostics.collectionStages().stream()
                .filter(stage -> "FAILED".equals(stage.status())).count();
        long skipped = diagnostics.collectionStages().stream()
                .filter(stage -> "SKIPPED".equals(stage.status())).count();
        collection.put("status", failed == 0 && skipped == 0 ? "COMPLETE" : successful == 0 ? "FAILED" : "PARTIAL");
        collection.put("successfulSources", successful);
        collection.put("failedSources", failed);
        collection.put("skippedSources", skipped);
        collection.put("totalLatencyMs", diagnostics.collectionStages().stream()
                .mapToLong(KubernetesNamespaceDiagnostics.CollectionStage::latencyMs).sum());
        ArrayNode stages = collection.putArray("stages");
        diagnostics.collectionStages().stream().limit(24).forEach(stage -> {
            ObjectNode item = stages.addObject();
            item.put("source", stage.source());
            item.put("status", stage.status());
            item.put("itemCount", stage.itemCount());
            item.put("latencyMs", stage.latencyMs());
            if (stage.detail() != null && !stage.detail().isBlank()) {
                item.put("detail", truncate(stage.detail(), 300));
            }
        });
        if (diagnostics.partial()) {
            root.put("confidence", Math.min(root.path("confidence").asDouble(0.5), 0.5));
            collection.put("operatorMessageCode", "PARTIAL_COLLECTION");
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
