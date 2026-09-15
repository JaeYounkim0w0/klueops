package io.strato.aiops.application.port.out;

import java.time.Duration;

public interface ChartAcquisitionPort {
    byte[] fetch(FetchRequest request);

    record FetchRequest(String repositoryUrl, String packageName, String version, String contentUrl,
                        Duration timeout) {
    }
}
