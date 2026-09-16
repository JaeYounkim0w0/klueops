package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.dto.JobResponse;
import io.strato.aiops.application.port.in.CancelJobUseCase;
import io.strato.aiops.application.port.in.GetJobStatusUseCase;
import io.strato.aiops.domain.job.AsyncJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Jobs", description = "Async job status APIs")
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final GetJobStatusUseCase getJobStatusUseCase;
    private final CancelJobUseCase cancelJobUseCase;

    /** JobController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JobController(GetJobStatusUseCase getJobStatusUseCase, CancelJobUseCase cancelJobUseCase) {
        this.getJobStatusUseCase = getJobStatusUseCase;
        this.cancelJobUseCase = cancelJobUseCase;
    }

    /** JobController의 getJob 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Get async job status")
    @GetMapping("/{jobId}")
    public JobResponse getJob(@PathVariable UUID jobId) {
        AsyncJob job = getJobStatusUseCase.getJob(jobId);
        return JobResponse.from(job);
    }

    /** JobController의 listJobs 처리 결과를 조회해 반환한다. */
    @Operation(summary = "List recent async jobs")
    @GetMapping
    public List<JobResponse> listJobs() {
        return getJobStatusUseCase.listRecentJobs().stream()
                .map(JobResponse::from)
                .toList();
    }

    /** JobController의 cancelJob 처리 조건의 충족 여부를 판단한다. */
    @Operation(summary = "Cancel async job")
    @PostMapping("/{jobId}/cancel")
    public JobResponse cancelJob(@PathVariable UUID jobId, HttpServletRequest request) {
        return JobResponse.from(cancelJobUseCase.cancelJob(jobId, actor(request), requestId(request)));
    }

    /** JobController의 actor 처리에 필요한 업무 로직을 수행한다. */
    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    /** JobController의 requestId 처리에 필요한 업무 로직을 수행한다. */
    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }
}
