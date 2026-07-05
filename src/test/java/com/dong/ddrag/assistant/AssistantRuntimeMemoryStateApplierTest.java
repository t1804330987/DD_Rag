package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryAction;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryChange;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryState;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryStateApplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRuntimeMemoryStateApplierTest {

    private final AssistantRuntimeMemoryStateApplier applier = new AssistantRuntimeMemoryStateApplier();

    @Test
    void shouldRoundTripStateAsJson() throws Exception {
        AssistantRuntimeMemoryState state = new AssistantRuntimeMemoryState(
                1L,
                List.of(new AssistantRuntimeMemoryState.Conclusion(
                        "rm_1",
                        "论文选题",
                        "方案 A",
                        3001L,
                        1716380000000L,
                        List.of()
                )),
                null
        );

        ObjectMapper objectMapper = new ObjectMapper();
        AssistantRuntimeMemoryState restored = objectMapper.readValue(
                objectMapper.writeValueAsString(state),
                AssistantRuntimeMemoryState.class
        );

        assertThat(restored.conclusions()).hasSize(1);
        assertThat(restored.conclusions().getFirst().keyLabel()).isEqualTo("论文选题");
        assertThat(restored.conclusions().getFirst().activeValue()).isEqualTo("方案 A");
    }

    @Test
    void shouldAddConclusionWithSequentialKeyId() {
        AssistantRuntimeMemoryState next = applier.apply(
                AssistantRuntimeMemoryState.empty(),
                List.of(new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.ADD,
                        null,
                        "论文选题",
                        "方案 A",
                        "用户明确选择方案 A",
                        null
                )),
                3001L,
                1716380000000L
        );

        assertThat(next.conclusions()).hasSize(1);
        assertThat(next.conclusions().getFirst().keyId()).isEqualTo("rm_1");
        assertThat(next.conclusions().getFirst().activeValue()).isEqualTo("方案 A");
        assertThat(next.version()).isEqualTo(1L);
    }

    @Test
    void shouldReplaceConclusionAndKeepAtMostThreeSupersededValues() {
        AssistantRuntimeMemoryState state = applier.apply(
                AssistantRuntimeMemoryState.empty(),
                List.of(new AssistantRuntimeMemoryChange(AssistantRuntimeMemoryAction.ADD, null, "论文选题", "方案 A", "init", null)),
                3001L,
                1000L
        );
        state = replace(state, "方案 B", 3002L, 2000L);
        state = replace(state, "方案 C", 3003L, 3000L);
        state = replace(state, "方案 D", 3004L, 4000L);
        state = replace(state, "方案 E", 3005L, 5000L);

        AssistantRuntimeMemoryState.Conclusion conclusion = state.conclusions().getFirst();
        assertThat(conclusion.activeValue()).isEqualTo("方案 E");
        assertThat(conclusion.supersededValues()).hasSize(3);
        assertThat(conclusion.supersededValues())
                .extracting(AssistantRuntimeMemoryState.SupersededValue::value)
                .containsExactly("方案 D", "方案 C", "方案 B");
    }

    @Test
    void shouldRevokeConclusionAsTombstone() {
        AssistantRuntimeMemoryState state = applier.apply(
                AssistantRuntimeMemoryState.empty(),
                List.of(new AssistantRuntimeMemoryChange(AssistantRuntimeMemoryAction.ADD, null, "论文选题", "方案 A", "init", null)),
                3001L,
                1000L
        );

        AssistantRuntimeMemoryState next = applier.apply(
                state,
                List.of(new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.REVOKE,
                        "rm_1",
                        null,
                        null,
                        "用户说不用方案 A 了",
                        null
                )),
                3002L,
                2000L
        );

        AssistantRuntimeMemoryState.Conclusion conclusion = next.conclusions().getFirst();
        assertThat(conclusion.activeValue()).isNull();
        assertThat(conclusion.supersededValues()).hasSize(1);
        assertThat(conclusion.supersededValues().getFirst().reason()).isEqualTo("用户说不用方案 A 了");
    }

    @Test
    void shouldStorePendingConfirmationWithoutChangingActiveConclusion() {
        AssistantRuntimeMemoryState state = applier.apply(
                AssistantRuntimeMemoryState.empty(),
                List.of(new AssistantRuntimeMemoryChange(AssistantRuntimeMemoryAction.ADD, null, "论文选题", "方案 A", "init", null)),
                3001L,
                1000L
        );

        AssistantRuntimeMemoryState next = applier.apply(
                state,
                List.of(new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.ASK_CONFIRMATION,
                        "rm_1",
                        "论文选题",
                        "方案 B",
                        "用户表达不明确",
                        "你是想把论文选题从方案 A 改成方案 B 吗？"
                )),
                3002L,
                2000L,
                "B 好像也可以，按这个写一版"
        );

        assertThat(next.conclusions().getFirst().activeValue()).isEqualTo("方案 A");
        assertThat(next.pending()).isNotNull();
        assertThat(next.pending().originalUserRequest()).isEqualTo("B 好像也可以，按这个写一版");
    }

    private AssistantRuntimeMemoryState replace(
            AssistantRuntimeMemoryState state,
            String value,
            Long messageId,
            Long now
    ) {
        return applier.apply(
                state,
                List.of(new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.REPLACE,
                        "rm_1",
                        null,
                        value,
                        "replace",
                        null
                )),
                messageId,
                now
        );
    }
}
