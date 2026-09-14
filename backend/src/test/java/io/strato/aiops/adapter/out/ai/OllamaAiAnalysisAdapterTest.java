package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAiAnalysisAdapterTest {

    @Test
    void callsChatClientWithStatelessJsonModeAndValidatesResponse() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                ```json
                {
                  "schemaVersion":"analysis-result.v1",
                  "summary":"ok",
                  "severity":"INFO",
                  "confidence":0.8,
                  "riskScore":10,
                  "findings":[],
                  "rootCauses":[],
                  "logAnalysis":[],
                  "performance":{"summary":"stable","bottlenecks":[],"improvements":[]},
                  "scaling":{"summary":"no action","scaleUpCandidates":[],"hpaRecommendations":[],"capacityNotes":[]},
                  "recommendations":[],
                  "operationsGuide":{"summary":"continue monitoring","shortTerm":[],"mediumTerm":[],"questionsForOperator":[]},
                  "nextActions":[],
                  "verificationCommands":[],
                  "evidence":{"resources":[],"events":[],"logs":[]}
                }
                ```
                """);
        OllamaAiAnalysisAdapter adapter = new OllamaAiAnalysisAdapter(
                ChatClient.builder(chatModel),
                new ObjectMapper(),
                "test-model",
                0.1
        );

        String result = adapter.analyze("namespace=default\npod=api status=Running");

        assertThat(result).contains("\"schemaVersion\":\"analysis-result.v1\"");
        assertThat(result).contains("\"operationsGuide\"");
        assertThat(result).contains("\"scaling\"");
        assertThat(result).contains("\"riskForecast\"");
        assertThat(result).contains("\"changeTimeline\"");
        assertThat(result).contains("\"runbookActions\"");
        List<String> promptTexts = chatModel.prompt.getInstructions().stream()
                .map(Message::getText)
                .toList();
        assertThat(promptTexts).anyMatch(text -> text.contains("Return only JSON compatible with analysis-result.v1"));
        assertThat(promptTexts).anyMatch(text -> text.contains("Do not infer debug mode is enabled"));
        assertThat(promptTexts).anyMatch(text -> text.contains("metric-based performance is not available"));
        assertThat(promptTexts).anyMatch(text -> text.contains("Honor the analysisScope from the context"));
        assertThat(promptTexts).anyMatch(text -> text.contains("cluster-live-inventory"));
        assertThat(promptTexts).anyMatch(text -> text.contains("\"riskForecast\""));
        assertThat(promptTexts).anyMatch(text -> text.contains("\"changeTimeline\""));
        assertThat(promptTexts.get(promptTexts.size() - 1)).contains("namespace=default");
        assertThat(chatModel.prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
        OllamaChatOptions options = (OllamaChatOptions) chatModel.prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("test-model");
        assertThat(options.getTemperature()).isEqualTo(0.1);
        assertThat(options.getFormat()).isEqualTo("json");
    }

    @Test
    void rejectsInvalidJsonAnalysisResponses() {
        CapturingChatModel chatModel = new CapturingChatModel("not-json");
        OllamaAiAnalysisAdapter adapter = new OllamaAiAnalysisAdapter(
                ChatClient.builder(chatModel),
                new ObjectMapper(),
                "test-model",
                0.1
        );

        assertThatThrownBy(() -> adapter.analyze("namespace=default"))
                .isInstanceOf(AiAnalysisResponseFormatException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void repairsJsonMissingOptionalAnalysisSections() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "schemaVersion":"analysis-result.v1",
                  "summary":"ok",
                  "severity":"INFO",
                  "findings":[],
                  "rootCauses":[],
                  "recommendations":[],
                  "evidence":{}
                }
                """);
        OllamaAiAnalysisAdapter adapter = new OllamaAiAnalysisAdapter(
                ChatClient.builder(chatModel),
                new ObjectMapper(),
                "test-model",
                0.1
        );

        String result = adapter.analyze("namespace=default");

        assertThat(result).contains("\"verificationCommands\":[]");
        assertThat(result).contains("\"logAnalysis\":[]");
        assertThat(result).contains("\"performance\"");
        assertThat(result).contains("\"riskForecast\"");
        assertThat(result).contains("\"changeTimeline\":[]");
        assertThat(result).contains("\"runbookActions\":[]");
        assertThat(result).contains("\"resources\":[]");
    }

    @Test
    void repairsJsonMissingSchemaVersion() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "summary":"cluster posture needs verification",
                  "severity":"MEDIUM",
                  "findings":[],
                  "rootCauses":[],
                  "recommendations":[],
                  "evidence":{}
                }
                """);
        OllamaAiAnalysisAdapter adapter = new OllamaAiAnalysisAdapter(
                ChatClient.builder(chatModel),
                new ObjectMapper(),
                "test-model",
                0.1
        );

        String result = adapter.analyze("analysisScope=cluster-live-inventory");

        assertThat(result).contains("\"schemaVersion\":\"analysis-result.v1\"");
        assertThat(result).contains("\"summary\":\"cluster posture needs verification\"");
    }

    @Test
    void repairsJsonSectionsWithWrongContainerTypes() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "schemaVersion":"analysis-result.v1",
                  "summary":"cluster posture needs verification",
                  "severity":"LOW",
                  "findings":{},
                  "rootCauses":"none",
                  "logAnalysis":{},
                  "performance":[],
                  "scaling":"unknown",
                  "riskForecast":[],
                  "changeTimeline":{},
                  "runbookActions":"kubectl get pods -A",
                  "recommendations":{},
                  "operationsGuide":[],
                  "nextActions":{},
                  "verificationCommands":"kubectl get events -A",
                  "evidence":[]
                }
                """);
        OllamaAiAnalysisAdapter adapter = new OllamaAiAnalysisAdapter(
                ChatClient.builder(chatModel),
                new ObjectMapper(),
                "test-model",
                0.1
        );

        String result = adapter.analyze("analysisScope=cluster-live-inventory");

        assertThat(result).contains("\"findings\":[]");
        assertThat(result).contains("\"rootCauses\":[]");
        assertThat(result).contains("\"logAnalysis\":[]");
        assertThat(result).contains("\"verificationCommands\":[]");
        assertThat(result).contains("\"performance\":{\"summary\"");
        assertThat(result).contains("\"evidence\":{\"resources\":[],\"events\":[],\"logs\":[]}");
    }

    @Test
    void repairsJsonMissingRequiredSummaryAndSeverity() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {
                  "schemaVersion":"analysis-result.v1",
                  "findings":[{"title":"Deployment needs verification"}],
                  "rootCauses":[],
                  "recommendations":[],
                  "evidence":{},
                  "riskScore":42
                }
                """);
        OllamaAiAnalysisAdapter adapter = new OllamaAiAnalysisAdapter(
                ChatClient.builder(chatModel),
                new ObjectMapper(),
                "test-model",
                0.1
        );

        String result = adapter.analyze("analysisScope=cluster-live-inventory");

        assertThat(result).contains("\"summary\":\"Deployment needs verification\"");
        assertThat(result).contains("\"severity\":\"MEDIUM\"");
        assertThat(result).contains("\"confidence\":0.35");
    }

    private static final class CapturingChatModel implements ChatModel {

        private final String responseText;
        private Prompt prompt;

        private CapturingChatModel(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage(responseText),
                    ChatGenerationMetadata.builder().finishReason("stop").build()
            )), ChatResponseMetadata.builder().model("test-model").build());
        }
    }
}
