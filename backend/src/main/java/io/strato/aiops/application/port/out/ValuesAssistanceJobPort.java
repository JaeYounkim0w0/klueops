package io.strato.aiops.application.port.out;

import java.util.UUID;
import java.util.Optional;

/** Values 생성의 소유권과 암호화 payload를 저장하는 비동기 작업 경계이다. */
public interface ValuesAssistanceJobPort {
    /** 작업과 소유권을 한 transaction으로 생성한다. */
    UUID create(UUID tenant, String actor, UUID chartVersion, String encryptedRequest);
    /** 해당 사용자와 Tenant의 작업만 조회한다. */
    Optional<Entry> find(UUID id, UUID tenant, String actor);
    /** 대기 작업을 한 worker만 실행하도록 조건부 전환한다. */
    boolean start(UUID id);
    /** 생성 검증 실패도 결과를 보관하되 Job Center에는 실패로 표시한다. */
    void complete(UUID id, String encryptedResult, boolean generationFailed);
    /** 활성 작업의 안전한 실패 사유만 저장한다. */
    void fail(UUID id, String reason);
    record Entry(UUID id, UUID tenant, String actor, UUID chartVersion, String request, String result) { }
}
