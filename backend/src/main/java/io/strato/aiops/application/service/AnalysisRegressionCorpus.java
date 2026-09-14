package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AnalysisRegressionCorpus {

    private static final String RESOURCE = "ai-regression/operational-corpus-v2.json";
    private final List<Fixture> fixtures;

    public AnalysisRegressionCorpus(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            fixtures = List.copyOf(objectMapper.readValue(input, new TypeReference<List<Fixture>>() { }));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load AI regression corpus " + RESOURCE, exception);
        }
        validate(fixtures);
    }

    public List<Fixture> fixtures() {
        return fixtures;
    }

    private void validate(List<Fixture> values) {
        if (values.size() < 50) throw new IllegalStateException("AI regression corpus requires at least 50 cases");
        Set<String> ids = new HashSet<>();
        Set<String> signals = new HashSet<>();
        for (Fixture fixture : values) {
            if (!ids.add(fixture.id())) throw new IllegalStateException("Duplicate fixture id: " + fixture.id());
            if (!signals.add(fixture.signal())) throw new IllegalStateException("Duplicate fixture signal: " + fixture.id());
            if (fixture.evidence() == null || fixture.evidence().size() < 2) {
                throw new IllegalStateException("Fixture requires two evidence records: " + fixture.id());
            }
            if (fixture.commands() == null || fixture.commands().isEmpty()) {
                throw new IllegalStateException("Fixture requires a verification command: " + fixture.id());
            }
        }
    }

    public record Fixture(String id, String title, String expectedCategory, String resourceKind,
                          String resourceName, String signal, List<String> evidence,
                          List<String> commands, boolean requiresMutationGuard, boolean expectedAbstention) {
    }
}
