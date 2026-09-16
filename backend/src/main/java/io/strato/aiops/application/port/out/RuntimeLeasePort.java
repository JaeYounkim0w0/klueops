package io.strato.aiops.application.port.out;

import java.time.Duration;

public interface RuntimeLeasePort {

    /** RuntimeLeasePort의 acquireOrRenew 처리 계약을 정의한다. */
    boolean acquireOrRenew(String leaseKey, Duration ttl);

    /** RuntimeLeasePort의 release 처리 계약을 정의한다. */
    void release(String leaseKey);

    /** RuntimeLeasePort의 localOnly 처리에 필요한 업무 로직을 수행한다. */
    static RuntimeLeasePort localOnly() {
        return new RuntimeLeasePort() {
            /** 익명 구현체의 acquireOrRenew 처리에 필요한 업무 로직을 수행한다. */
            @Override public boolean acquireOrRenew(String leaseKey, Duration ttl) { return true; }
            /** 익명 구현체의 release 처리에 필요한 업무 로직을 수행한다. */
            @Override public void release(String leaseKey) { }
        };
    }
}
