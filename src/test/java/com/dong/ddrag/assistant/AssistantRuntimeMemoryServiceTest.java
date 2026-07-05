package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryAction;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryChange;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryExtractor;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryService;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryState;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryStateApplier;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class AssistantRuntimeMemoryServiceTest {

    private final AssistantSessionContextMapper contextMapper = mock(AssistantSessionContextMapper.class);
    private final AssistantMessageMapper messageMapper = mock(AssistantMessageMapper.class);
    private final AssistantRuntimeMemoryExtractor extractor = mock(AssistantRuntimeMemoryExtractor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldApplyExplicitReplaceAndContinueCurrentMessage() throws Exception {
        AssistantRuntimeMemoryService service = createService();
        given(contextMapper.selectBySessionId(2001L)).willReturn(contextWithState(1L, stateWithConclusion("方案 A")));
        given(extractor.extract(any(), any(), eq("把论文选题改成方案 B"))).willReturn(List.of(
                new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.REPLACE,
                        "rm_1",
                        null,
                        "方案 B",
                        "用户说改成方案 B",
                        null
                )
        ));
        given(contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = service.beforeAnswer(
                1001L,
                2001L,
                3002L,
                AssistantToolMode.CHAT,
                null,
                "把论文选题改成方案 B"
        );

        assertThat(decision.requiresConfirmation()).isFalse();
        assertThat(decision.effectiveUserMessage()).isEqualTo("把论文选题改成方案 B");
        ArgumentCaptor<AssistantSessionContextEntity> captor = ArgumentCaptor.forClass(AssistantSessionContextEntity.class);
        then(contextMapper).should().updateShortTermMemoryWithVersion(captor.capture(), eq(1L));
        AssistantRuntimeMemoryState saved = objectMapper.readValue(
                captor.getValue().getRuntimeMemoryState(),
                AssistantRuntimeMemoryState.class
        );
        assertThat(saved.conclusions().getFirst().activeValue()).isEqualTo("方案 B");
    }

    @Test
    void shouldAskConfirmationForAmbiguousReplacement() throws Exception {
        AssistantRuntimeMemoryService service = createService();
        given(contextMapper.selectBySessionId(2001L)).willReturn(contextWithState(1L, stateWithConclusion("方案 A")));
        given(extractor.extract(any(), any(), eq("B 好像也可以，按这个写一版"))).willReturn(List.of(
                new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.ASK_CONFIRMATION,
                        "rm_1",
                        "论文选题",
                        "方案 B",
                        "表达模糊",
                        "你是想把论文选题从方案 A 改成方案 B 吗？"
                )
        ));
        given(contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = service.beforeAnswer(
                1001L,
                2001L,
                3002L,
                AssistantToolMode.CHAT,
                null,
                "B 好像也可以，按这个写一版"
        );

        assertThat(decision.requiresConfirmation()).isTrue();
        assertThat(decision.assistantReply()).isEqualTo("你是想把论文选题从方案 A 改成方案 B 吗？");
        then(contextMapper).should().updateShortTermMemoryWithVersion(any(), eq(1L));
    }

    @Test
    void shouldConfirmPendingAndResumeOriginalRequest() throws Exception {
        AssistantRuntimeMemoryService service = createService();
        AssistantRuntimeMemoryState pendingState = new AssistantRuntimeMemoryState(
                2L,
                stateWithConclusion("方案 A").conclusions(),
                new AssistantRuntimeMemoryState.Pending(
                        AssistantRuntimeMemoryAction.ASK_CONFIRMATION,
                        "rm_1",
                        "论文选题",
                        "方案 B",
                        3002L,
                        "B 好像也可以，按这个写一版",
                        "你是想改成方案 B 吗？",
                        1_000L
                )
        );
        given(contextMapper.selectBySessionId(2001L)).willReturn(contextWithState(1L, pendingState));
        given(contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = service.beforeAnswer(
                1001L,
                2001L,
                3003L,
                AssistantToolMode.CHAT,
                null,
                "是的"
        );

        assertThat(decision.requiresConfirmation()).isFalse();
        assertThat(decision.effectiveUserMessage()).isEqualTo("B 好像也可以，按这个写一版");
        then(extractor).shouldHaveNoInteractions();
    }

    @Test
    void shouldCancelPendingWhenUserRejectsConfirmation() throws Exception {
        AssistantRuntimeMemoryService service = createService();
        AssistantRuntimeMemoryState pendingState = new AssistantRuntimeMemoryState(
                2L,
                stateWithConclusion("方案 A").conclusions(),
                new AssistantRuntimeMemoryState.Pending(
                        AssistantRuntimeMemoryAction.ASK_CONFIRMATION,
                        "rm_1",
                        "论文选题",
                        "方案 B",
                        3002L,
                        "B 好像也可以，按这个写一版",
                        "你是想改成方案 B 吗？",
                        1_000L
                )
        );
        given(contextMapper.selectBySessionId(2001L)).willReturn(contextWithState(1L, pendingState));
        given(contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = service.beforeAnswer(
                1001L,
                2001L,
                3003L,
                AssistantToolMode.CHAT,
                null,
                "不确认"
        );

        assertThat(decision.requiresConfirmation()).isFalse();
        assertThat(decision.effectiveUserMessage()).isEqualTo("不确认");
        ArgumentCaptor<AssistantSessionContextEntity> captor = ArgumentCaptor.forClass(AssistantSessionContextEntity.class);
        then(contextMapper).should().updateShortTermMemoryWithVersion(captor.capture(), eq(1L));
        AssistantRuntimeMemoryState saved = objectMapper.readValue(
                captor.getValue().getRuntimeMemoryState(),
                AssistantRuntimeMemoryState.class
        );
        assertThat(saved.pending()).isNull();
        assertThat(saved.conclusions().getFirst().activeValue()).isEqualTo("方案 A");
        then(extractor).shouldHaveNoInteractions();
    }

    @Test
    void shouldContinueWhenExtractorFails() throws Exception {
        AssistantRuntimeMemoryService service = createService();
        given(contextMapper.selectBySessionId(2001L)).willReturn(contextWithState(1L, stateWithConclusion("方案 A")));
        given(extractor.extract(any(), any(), any())).willThrow(new IllegalArgumentException("bad json"));

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = service.beforeAnswer(
                1001L,
                2001L,
                3002L,
                AssistantToolMode.CHAT,
                null,
                "把论文选题改成方案 B"
        );

        assertThat(decision.effectiveUserMessage()).isEqualTo("把论文选题改成方案 B");
        assertThat(decision.requiresConfirmation()).isFalse();
        then(contextMapper).should(never()).updateShortTermMemoryWithVersion(any(), any());
    }

    @Test
    void shouldSkipExtractorForOrdinaryUsageQuestion() throws Exception {
        AssistantRuntimeMemoryService service = createService();
        given(contextMapper.selectBySessionId(2001L)).willReturn(contextWithState(1L, stateWithConclusion("方案 A")));

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = service.beforeAnswer(
                1001L,
                2001L,
                3002L,
                AssistantToolMode.CHAT,
                null,
                "这个功能怎么用"
        );

        assertThat(decision.effectiveUserMessage()).isEqualTo("这个功能怎么用");
        assertThat(decision.requiresConfirmation()).isFalse();
        then(extractor).shouldHaveNoInteractions();
        then(contextMapper).should(never()).updateShortTermMemoryWithVersion(any(), any());
    }

    @Test
    void shouldRetryOnceWhenVersionConflict() throws Exception {
        AssistantRuntimeMemoryService service = createService();
        AssistantSessionContextEntity first = contextWithState(1L, stateWithConclusion("方案 A"));
        AssistantSessionContextEntity second = contextWithState(2L, stateWithConclusion("方案 A"));
        given(contextMapper.selectBySessionId(2001L)).willReturn(first, first, second);
        given(extractor.extract(any(), any(), eq("把论文选题改成方案 B"))).willReturn(List.of(
                new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.REPLACE,
                        "rm_1",
                        null,
                        "方案 B",
                        "用户说改成方案 B",
                        null
                )
        ));
        given(contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(0);
        given(contextMapper.updateShortTermMemoryWithVersion(any(), eq(2L))).willReturn(1);

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = service.beforeAnswer(
                1001L,
                2001L,
                3002L,
                AssistantToolMode.CHAT,
                null,
                "把论文选题改成方案 B"
        );

        assertThat(decision.effectiveUserMessage()).isEqualTo("把论文选题改成方案 B");
        then(contextMapper).should(times(2)).updateShortTermMemoryWithVersion(any(), any());
    }

    private AssistantRuntimeMemoryService createService() {
        return new AssistantRuntimeMemoryService(
                contextMapper,
                messageMapper,
                extractor,
                new AssistantRuntimeMemoryStateApplier(),
                objectMapper,
                Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC)
        );
    }

    private AssistantSessionContextEntity contextWithState(Long contextVersion, AssistantRuntimeMemoryState state) throws Exception {
        AssistantSessionContextEntity entity = new AssistantSessionContextEntity();
        entity.setSessionId(2001L);
        entity.setContextVersion(contextVersion);
        entity.setRuntimeMemoryState(objectMapper.writeValueAsString(state));
        return entity;
    }

    private AssistantRuntimeMemoryState stateWithConclusion(String value) {
        return new AssistantRuntimeMemoryState(
                1L,
                List.of(new AssistantRuntimeMemoryState.Conclusion(
                        "rm_1",
                        "论文选题",
                        value,
                        3001L,
                        1_000L,
                        List.of()
                )),
                null
        );
    }
}
