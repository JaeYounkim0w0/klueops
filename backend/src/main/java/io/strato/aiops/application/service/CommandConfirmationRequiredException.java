package io.strato.aiops.application.service;

public class CommandConfirmationRequiredException extends RuntimeException {
    private final String safety;
    private final String target;

    /** CommandConfirmationRequiredException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandConfirmationRequiredException(String safety, String target) {
        super("Command confirmation is required");
        this.safety = safety;
        this.target = target;
    }

    /** CommandConfirmationRequiredException의 safety 처리에 필요한 업무 로직을 수행한다. */
    public String safety() { return safety; }
    /** CommandConfirmationRequiredException의 target 처리에 필요한 업무 로직을 수행한다. */
    public String target() { return target; }
}

