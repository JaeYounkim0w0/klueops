package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.AnalysisStatus;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.OperationsHotspot;
import io.strato.aiops.domain.operations.OperationsModels.OperationsScorecard;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;
import io.strato.aiops.domain.operations.OperationsModels.WeeklyTrend;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class OperationsScorecardQueryService {

    private static final int SAMPLE_LIMIT = 500;

    private final OperationsRepositoryPort operationsRepository;
    private final AnalysisSessionRepositoryPort analysisRepository;
    private final AnalysisAssuranceRepositoryPort assuranceRepository;

    /** OperationsScorecardQueryService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationsScorecardQueryService(OperationsRepositoryPort operationsRepository,
                                           AnalysisSessionRepositoryPort analysisRepository,
                                           AnalysisAssuranceRepositoryPort assuranceRepository) {
        this.operationsRepository = operationsRepository;
        this.analysisRepository = analysisRepository;
        this.assuranceRepository = assuranceRepository;
    }

    /** OperationsScorecardQueryService의 getScorecard 처리 결과를 조회해 반환한다. */
    public OperationsScorecard getScorecard() {
        List<Incident> incidents = operationsRepository.findIncidents(null, null, null, null, SAMPLE_LIMIT);
        List<AnalysisSession> analyses = analysisRepository.findRecent(null, null, null, SAMPLE_LIMIT);
        List<WatchSignalGroup> groups = assuranceRepository.findWatchSignalGroups(null, null, null, SAMPLE_LIMIT);
        Set<UUID> incidentIds = incidents.stream().map(Incident::id).collect(Collectors.toSet());
        Map<UUID, List<IncidentActivity>> activities = operationsRepository
                .findIncidentActivitiesByIncidentIds(incidentIds).stream()
                .collect(Collectors.groupingBy(IncidentActivity::incidentId));
        List<Long> acknowledgementMinutes = new ArrayList<>();
        List<Long> resolutionMinutes = new ArrayList<>();
        for (Incident incident : incidents) {
            List<IncidentActivity> activity = activities.getOrDefault(incident.id(), List.of());
            activity.stream().filter(item -> item.toState() == IncidentState.ACKNOWLEDGED).findFirst()
                    .ifPresent(item -> acknowledgementMinutes.add(Duration.between(incident.firstDetectedAt(),
                            item.createdAt()).toMinutes()));
            activity.stream().filter(item -> item.toState() == IncidentState.RESOLVED).reduce((a, b) -> b)
                    .ifPresent(item -> resolutionMinutes.add(Duration.between(incident.firstDetectedAt(),
                            item.createdAt()).toMinutes()));
        }
        Instant now = Instant.now();
        Instant week = now.minus(Duration.ofDays(7));
        Instant previousWeek = now.minus(Duration.ofDays(14));
        int currentIncidents = count(incidents, item -> !item.firstDetectedAt().isBefore(week));
        int previousIncidents = count(incidents, item -> item.firstDetectedAt().isBefore(week)
                && !item.firstDetectedAt().isBefore(previousWeek));
        int currentResolved = count(incidents, item -> item.state() == IncidentState.RESOLVED
                && !item.lastDetectedAt().isBefore(week));
        int previousResolved = count(incidents, item -> item.state() == IncidentState.RESOLVED
                && item.lastDetectedAt().isBefore(week) && !item.lastDetectedAt().isBefore(previousWeek));
        int currentAnalyses = count(analyses, item -> !item.createdAt().isBefore(week));
        int previousAnalyses = count(analyses, item -> item.createdAt().isBefore(week)
                && !item.createdAt().isBefore(previousWeek));
        List<OperationsHotspot> hotspots = incidents.stream()
                .collect(Collectors.groupingBy(item -> String.join("|", item.clusterId().toString(),
                        defaultText(item.namespace()), defaultText(item.resourceKind()), defaultText(item.resourceName()))))
                .values().stream().map(values -> {
                    Incident latest = values.stream().max(Comparator.comparing(Incident::lastDetectedAt)).orElseThrow();
                    return new OperationsHotspot(latest.clusterId(), latest.clusterName(), latest.namespace(),
                            latest.resourceKind(), latest.resourceName(), values.size(),
                            values.stream().mapToInt(Incident::reopenCount).sum(), latest.severity(),
                            latest.lastDetectedAt());
                }).sorted(Comparator.comparingInt(OperationsHotspot::recurrenceCount).reversed()
                        .thenComparing(OperationsHotspot::lastDetectedAt).reversed()).limit(10).toList();
        int succeeded = count(analyses, item -> item.status() == AnalysisStatus.SUCCEEDED);
        int failed = count(analyses, item -> item.status() == AnalysisStatus.FAILED);
        int fallback = count(analyses, item -> containsIgnoreCase(item.resultSummary(), "fallback")
                || containsIgnoreCase(item.resultJson(), "\"fallback\""));
        int resolved = count(incidents, item -> item.state() == IncidentState.RESOLVED);
        return new OperationsScorecard(now, incidents.size(),
                count(incidents, item -> item.state() != IncidentState.RESOLVED), resolved,
                average(acknowledgementMinutes), average(resolutionMinutes),
                percentage(count(incidents, item -> item.reopenCount() > 0), incidents.size()),
                percentage(resolved, incidents.size()), percentage(succeeded, succeeded + failed),
                percentage(fallback, analyses.size()), count(groups, item -> "OPEN".equals(item.state())),
                count(groups, item -> "INCIDENT_CREATED".equals(item.state())),
                count(groups, item -> "SUPPRESSED".equals(item.state())),
                new WeeklyTrend(currentIncidents, previousIncidents, currentResolved, previousResolved,
                        currentAnalyses, previousAnalyses,
                        currentIncidents < previousIncidents ? "IMPROVING"
                                : currentIncidents > previousIncidents ? "DEGRADING" : "STABLE"), hotspots);
    }

    /** OperationsScorecardQueryService의 count 처리에 필요한 업무 로직을 수행한다. */
    private static <T> int count(List<T> values, Predicate<T> predicate) {
        return (int) values.stream().filter(predicate).count();
    }

    /** OperationsScorecardQueryService의 average 처리에 필요한 업무 로직을 수행한다. */
    private static long average(List<Long> values) {
        return values.isEmpty() ? 0L : Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    /** OperationsScorecardQueryService의 percentage 처리에 필요한 업무 로직을 수행한다. */
    private static double percentage(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : Math.round((numerator * 1000.0) / denominator) / 10.0;
    }

    /** OperationsScorecardQueryService의 containsIgnoreCase 처리에 필요한 업무 로직을 수행한다. */
    private static boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    /** OperationsScorecardQueryService의 defaultText 처리에 필요한 업무 로직을 수행한다. */
    private static String defaultText(String value) {
        return value == null ? "" : value;
    }
}
