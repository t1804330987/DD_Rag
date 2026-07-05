package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryAction;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryChange;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryPromptExtractor;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantRuntimeMemoryPromptExtractorTest {

    @Test
    void shouldParseValidReplaceChange() {
        AssistantRuntimeMemoryPromptExtractor extractor = createExtractor("""
                {"changes":[{"action":"REPLACE","targetKeyId":"rm_1","value":"方案 B","reason":"用户说改成方案 B"}]}
                """);

        List<AssistantRuntimeMemoryChange> changes = extractor.extract(
                stateWithConclusion(),
                List.of(),
                "把论文选题改成方案 B"
        );

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().action()).isEqualTo(AssistantRuntimeMemoryAction.REPLACE);
        assertThat(changes.getFirst().targetKeyId()).isEqualTo("rm_1");
        assertThat(changes.getFirst().value()).isEqualTo("方案 B");
    }

    @Test
    void shouldRejectInvalidJson() {
        AssistantRuntimeMemoryPromptExtractor extractor = createExtractor("not json");

        assertThatThrownBy(() -> extractor.extract(stateWithConclusion(), List.of(), "改成 B"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime memory extractor 返回非法 JSON");
    }

    @Test
    void shouldRejectReplaceWithoutExistingTarget() {
        AssistantRuntimeMemoryPromptExtractor extractor = createExtractor("""
                {"changes":[{"action":"REPLACE","targetKeyId":"rm_missing","value":"方案 B","reason":"replace"}]}
                """);

        assertThatThrownBy(() -> extractor.extract(stateWithConclusion(), List.of(), "改成 B"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REPLACE/REVOKE 必须引用已有 targetKeyId");
    }

    @Test
    void shouldRejectAskConfirmationWithoutQuestion() {
        AssistantRuntimeMemoryPromptExtractor extractor = createExtractor("""
                {"changes":[{"action":"ASK_CONFIRMATION","targetKeyId":"rm_1","keyLabel":"论文选题","value":"方案 B","reason":"ambiguous"}]}
                """);

        assertThatThrownBy(() -> extractor.extract(stateWithConclusion(), List.of(), "按这个写"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASK_CONFIRMATION 必须包含 confirmationQuestion");
    }

    @Test
    void shouldRenderProductionPromptTemplate() {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/assistant/runtime-memory-extraction.st"))
                .build();

        Prompt prompt = promptTemplate.create(Map.of(
                "existingConclusions", "NONE",
                "recentMessages", "NONE",
                "currentUserMessage", "把论文选题改成方案 B"
        ));

        assertThat(prompt.getInstructions().getFirst().getText())
                .contains("把论文选题改成方案 B")
                .contains("\"changes\"");
    }

    private AssistantRuntimeMemoryPromptExtractor createExtractor(String modelOutput) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(modelOutput);
        return new AssistantRuntimeMemoryPromptExtractor(
                builder,
                PromptTemplate.builder()
                        .template("existing={existingConclusions}\nrecent={recentMessages}\ncurrent={currentUserMessage}")
                        .build(),
                new ObjectMapper()
        );
    }

    private AssistantRuntimeMemoryState stateWithConclusion() {
        return new AssistantRuntimeMemoryState(
                1L,
                List.of(new AssistantRuntimeMemoryState.Conclusion(
                        "rm_1",
                        "论文选题",
                        "方案 A",
                        3001L,
                        1000L,
                        List.of()
                )),
                null
        );
    }
}
