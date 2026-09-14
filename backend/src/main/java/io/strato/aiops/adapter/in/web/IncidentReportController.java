package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.IncidentReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Tag(name = "Incident Reports", description = "Audited incident evidence export APIs")
@RestController
@RequestMapping("/api/incidents/{incidentId}/report")
public class IncidentReportController {

    private final IncidentReportExportService service;

    public IncidentReportController(IncidentReportExportService service) {
        this.service = service;
    }

    @Operation(summary = "Export an incident report as Markdown, JSON, or ZIP evidence bundle")
    @GetMapping
    public ResponseEntity<StreamingResponseBody> export(@PathVariable UUID incidentId,
                                         @RequestParam(defaultValue = "markdown") String format,
                                         HttpServletRequest request) {
        var report = service.export(incidentId, format, actor(request),
                String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID)));
        MediaType mediaType = MediaType.parseMediaType(report.mediaType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", report.sha256())
                .body(report::writeTo);
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }
}
