package io.strato.aiops.application.port.out;

import java.time.Duration;

public interface RuntimeLeasePort {

    boolean acquireOrRenew(String leaseKey, Duration ttl);

    void release(String leaseKey);

    static RuntimeLeasePort localOnly() {
        return new RuntimeLeasePort() {
            @Override public boolean acquireOrRenew(String leaseKey, Duration ttl) { return true; }
            @Override public void release(String leaseKey) { }
        };
    }
}
