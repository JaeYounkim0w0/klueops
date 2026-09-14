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

    public JobController(GetJobStatusUseCase getJobStatusUseCase, CancelJobUseCase cancelJobUseCase) {
        this.getJobStatusUseCase = getJobStatusUseCase;
        this.cancelJobUseCase = cancelJobUseCase;
    }

    @Operation(summary = "Get async job status")
    @GetMapping("/{jobId}")
    public JobResponse getJob(@PathVariable UUID jobId) {
        AsyncJob job = getJobStatusUseCase.getJob(jobId);
        return JobResponse.from(job);
    }

    @Operation(summary = "List recent async jobs")
    @GetMapping
    public List<JobResponse> listJobs() {
        return getJobStatusUseCase.listRecentJobs().stream()
                .map(JobResponse::from)
                .toList();
    }

    @Operation(summary = "Cancel async job")
    @PostMapping("/{jobId}/cancel")
    public JobResponse cancelJob(@PathVariable UUID jobId, HttpServletRequest request) {
        return JobResponse.from(cancelJobUseCase.cancelJob(jobId, actor(request), requestId(request)));
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }
}
