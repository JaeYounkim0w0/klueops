package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.*;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executor;

/** 장시간 Values 생성을 HTTP 수명과 분리하고 사용자별 암호화 결과를 제공한다. */
@Service
public class ValuesAssistanceJobService {
    private final ValuesAssistanceJobPort store;
    private final AsyncJobRepositoryPort jobs;
    private final ApplicationDeliveryRepositoryPort charts;
    private final SecretCryptoPort crypto;
    private final ObjectMapper json;
    private final HelmValuesAssistanceService assistant;
    private final Executor executor;

    /** worker와 영속 저장소를 주입하며 외부 AI 호출 동안 transaction을 열지 않는다. */
    public ValuesAssistanceJobService(ValuesAssistanceJobPort store, AsyncJobRepositoryPort jobs,
            ApplicationDeliveryRepositoryPort charts, SecretCryptoPort crypto, ObjectMapper json,
            HelmValuesAssistanceService assistant, @Qualifier("valuesAssistanceExecutor") Executor executor) {
        this.store = store; this.jobs = jobs; this.charts = charts; this.crypto = crypto;
        this.json = json; this.assistant = assistant; this.executor = executor;
    }

    /** Tenant Chart 소유권을 먼저 확인하고 암호화한 요청을 제한 큐에 제출한다. */
    public UUID submit(UUID tenant, String actor, UUID version, String current, String instruction) {
        charts.findVersion(tenant, version).orElseThrow();
        Input input = new Input(current == null || current.isBlank() ? "{}" : current, instruction);
        UUID id = store.create(tenant, actor, version, seal(input));
        try { executor.execute(() -> run(id, tenant, actor)); }
        catch (RuntimeException rejected) { store.fail(id, "Values 생성 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요."); }
        return id;
    }

    /** 요청 소유권 확인 이후에만 공통 Job 상태를 반환한다. */
    public AsyncJob status(UUID id, UUID tenant, String actor) {
        store.find(id, tenant, actor).orElseThrow();
        return jobs.findById(id).orElseThrow();
    }

    /** 완료된 결과는 요청 사용자에게만 복호화하여 제공한다. */
    public Outcome result(UUID id, UUID tenant, String actor) {
        var entry = store.find(id, tenant, actor).orElseThrow();
        var status = jobs.findById(id).orElseThrow().status();
        if ((status != AsyncJobStatus.SUCCEEDED && status != AsyncJobStatus.FAILED) || entry.result() == null)
            throw new IllegalStateException("아직 결과를 조회할 수 없습니다.");
        return unseal(entry.result(), Outcome.class);
    }

    /** 취소되거나 timeout된 작업에는 늦게 도착한 결과를 적용하지 않는다. */
    private void run(UUID id, UUID tenant, String actor) {
        if (!store.start(id)) { store.fail(id, "종료된 작업입니다."); return; }
        try {
            var entry = store.find(id, tenant, actor).orElseThrow();
            Input input = unseal(entry.request(), Input.class);
            var result = assistant.assist(tenant, entry.chartVersion(), input.current(), input.instruction(),
                    () -> jobs.findById(id).map(job -> job.status() == AsyncJobStatus.RUNNING).orElse(false));
            store.complete(id, seal(new Outcome(digest(input.current()), result)), result.validationStatus().equals("GENERATION_FAILED"));
        } catch (Exception failure) {
            store.fail(id, "Values 생성 작업을 완료하지 못했습니다. AI 설정을 확인하고 다시 시도해 주세요.");
        }
    }

    /** 원문 Values와 결과는 공통 Secret 암호화 포맷으로 보호한다. */
    private String seal(Object value) {
        try { return json.writeValueAsString(crypto.encrypt(json.writeValueAsString(value))); }
        catch (Exception failure) { throw new IllegalStateException("Values 작업 암호화 실패", failure); }
    }

    /** 소유권 확인을 마친 payload만 복호화한다. */
    private <T> T unseal(String value, Class<T> type) {
        try { return json.readValue(crypto.decrypt(json.readValue(value, EncryptedSecret.class)), type); }
        catch (Exception failure) { throw new IllegalStateException("Values 작업 복호화 실패", failure); }
    }

    /** 재접속한 화면에서도 원본 Values가 같은지 확인할 비가역 지문을 만든다. */
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    public record Input(String current, String instruction) { }
    public record Outcome(String baseValuesDigest, HelmValuesAssistanceService.Result proposal) { }
}
