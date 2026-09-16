package io.strato.aiops.application.port.out;

@FunctionalInterface
public interface KubectlOutputListener {
    /** KubectlOutputListener의 onOutput 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    void onOutput(String channel, String text);
}

