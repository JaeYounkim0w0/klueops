package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.out.ai.AiAnalysisResponseFormatException;
import io.strato.aiops.adapter.out.ai.OllamaAiException;
import io.strato.aiops.adapter.out.kubernetes.KubernetesApiException;
import io.strato.aiops.application.service.AccountDisabledException;
import io.strato.aiops.application.service.ApplicationOperationConflictException;
import io.strato.aiops.application.service.CommandCapacityExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** GlobalExceptionHandler의 handleCredentialRevealDisabledException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(CredentialRevealDisabledException.class)
    public ProblemDetail handleCredentialRevealDisabledException(
            CredentialRevealDisabledException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problemDetail.setTitle("Credential reveal disabled");
        problemDetail.setProperty("code", "CREDENTIAL_REVEAL_DISABLED");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleIllegalArgumentException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problemDetail.setTitle("Invalid request");
        problemDetail.setProperty("code", "INVALID_REQUEST");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleApplicationOperationConflictException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(ApplicationOperationConflictException.class)
    public ProblemDetail handleApplicationOperationConflictException(
            ApplicationOperationConflictException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problemDetail.setTitle("Application operation conflict");
        problemDetail.setProperty("code", "APPLICATION_OPERATION_CONFLICT");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleMethodArgumentNotValidException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problemDetail.setTitle("Invalid request");
        problemDetail.setProperty("code", "INVALID_REQUEST");
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        problemDetail.setProperty("errors", errors);
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleNoSuchElementException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNoSuchElementException(NoSuchElementException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problemDetail.setTitle("Resource not found");
        problemDetail.setProperty("code", "RESOURCE_NOT_FOUND");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleNoResourceFoundException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(NoResourceFoundException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Requested route was not found");
        problemDetail.setTitle("Route not found");
        problemDetail.setProperty("code", "ROUTE_NOT_FOUND");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleOllamaAiException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(OllamaAiException.class)
    public ProblemDetail handleOllamaAiException(OllamaAiException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problemDetail.setTitle("Ollama unavailable");
        problemDetail.setProperty("code", "OLLAMA_UNAVAILABLE");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleAiAnalysisResponseFormatException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(AiAnalysisResponseFormatException.class)
    public ProblemDetail handleAiAnalysisResponseFormatException(
            AiAnalysisResponseFormatException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problemDetail.setTitle("AI analysis response invalid");
        problemDetail.setProperty("code", "AI_ANALYSIS_RESPONSE_INVALID");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleKubernetesApiException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(KubernetesApiException.class)
    public ProblemDetail handleKubernetesApiException(KubernetesApiException exception, HttpServletRequest request) {
        log.warn("Kubernetes API request failed. path={}, requestId={}",
                request.getRequestURI(), request.getAttribute(RequestAttributes.REQUEST_ID), exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problemDetail.setTitle("Kubernetes API unavailable");
        problemDetail.setProperty("code", "KUBERNETES_API_UNAVAILABLE");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleAccountDisabledException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(AccountDisabledException.class)
    public ProblemDetail handleAccountDisabledException(AccountDisabledException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problemDetail.setTitle("Account disabled");
        problemDetail.setProperty("code", "ACCOUNT_DISABLED");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleCommandCapacityExceededException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(CommandCapacityExceededException.class)
    public ProblemDetail handleCommandCapacityExceededException(
            CommandCapacityExceededException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problemDetail.setTitle("Kubernetes Console capacity exceeded");
        problemDetail.setProperty("code", "COMMAND_CAPACITY_EXCEEDED");
        problemDetail.setProperty("retryAfterSeconds", exception.retryAfterSeconds());
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleAccessDeniedException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problemDetail.setTitle("Access denied");
        problemDetail.setProperty("code", "ACCESS_DENIED");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 handleDisconnectedAsyncClient 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(IOException.class)
    public void handleDisconnectedAsyncClient(IOException exception, HttpServletRequest request) throws IOException {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        boolean disconnected = request.getRequestURI().equals("/api/operations/events")
                || message.contains("broken pipe") || message.contains("connection reset")
                || message.contains("response not usable");
        if (!disconnected) {
            throw exception;
        }
        log.debug("Client disconnected from async response. path={}, requestId={}, reason={}",
                request.getRequestURI(), request.getAttribute(RequestAttributes.REQUEST_ID), exception.getMessage());
    }

    /** GlobalExceptionHandler의 handleException 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API exception. path={}, requestId={}",
                request.getRequestURI(), request.getAttribute(RequestAttributes.REQUEST_ID), exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
        problemDetail.setTitle("Internal server error");
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");
        enrich(problemDetail, request);
        return problemDetail;
    }

    /** GlobalExceptionHandler의 enrich 처리에 필요한 업무 로직을 수행한다. */
    private void enrich(ProblemDetail problemDetail, HttpServletRequest request) {
        problemDetail.setProperty("requestId", request.getAttribute(RequestAttributes.REQUEST_ID));
        problemDetail.setProperty("correlationId", request.getAttribute(RequestAttributes.CORRELATION_ID));
        problemDetail.setProperty("timestamp", Instant.now());
    }
}
