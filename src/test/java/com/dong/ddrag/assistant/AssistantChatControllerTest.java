package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.controller.AssistantChatController;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.dong.ddrag.assistant.service.AssistantService;
import com.dong.ddrag.assistant.config.AssistantStreamExecutor;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.modelplatform.concurrency.AssistantTurnGuard;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AssistantChatControllerTest {

    @Test
    void shouldRejectSynchronousRequestBeforePersistingWhenSessionIsBusy() {
        AssistantService assistantService = mock(AssistantService.class);
        AssistantTurnGuard guard = new AssistantTurnGuard();
        try (AssistantTurnGuard.TurnPermit ignored = guard.acquire(2001L)) {
            AssistantChatController controller = new AssistantChatController(
                    assistantService,
                    new AssistantStreamExecutor(new ModelRuntimeProperties()),
                    guard
            );

            assertThatThrownBy(() -> controller.chat(
                    new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null, "request-busy"),
                    mock(jakarta.servlet.http.HttpServletRequest.class)
            )).isInstanceOf(BusinessException.class).hasMessage("SESSION_BUSY");
        }
        verifyNoInteractions(assistantService);
    }

    @Test
    void shouldIgnoreSendAndCompleteWhenEmitterAlreadyClosed() throws Exception {
        AssistantService assistantService = mock(AssistantService.class);
        AssistantChatController controller = new AssistantChatController(
                assistantService,
                new AssistantStreamExecutor(new ModelRuntimeProperties()),
                new AssistantTurnGuard()
        );
        SseEmitter emitter = new SseEmitter();
        AtomicBoolean closed = new AtomicBoolean(true);

        Method sendEventMethod = AssistantChatController.class.getDeclaredMethod(
                "sendEvent",
                SseEmitter.class,
                AssistantChatStreamEvent.class,
                AtomicBoolean.class
        );
        sendEventMethod.setAccessible(true);
        Method completeEmitterMethod = AssistantChatController.class.getDeclaredMethod(
                "completeEmitter",
                SseEmitter.class,
                AtomicBoolean.class,
                AtomicBoolean.class
        );
        completeEmitterMethod.setAccessible(true);

        assertThatCode(() -> sendEventMethod.invoke(
                controller,
                emitter,
                AssistantChatStreamEvent.error(
                        2001L,
                        AssistantToolMode.CHAT,
                        null,
                        "错误"
                ),
                closed
        )).doesNotThrowAnyException();
        assertThatCode(() -> completeEmitterMethod.invoke(
                controller,
                emitter,
                closed,
                new AtomicBoolean(false)
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldPreserveStableModelConfigurationErrorCodesForSse() throws Exception {
        AssistantChatController controller = new AssistantChatController(
                mock(AssistantService.class),
                new AssistantStreamExecutor(new ModelRuntimeProperties()),
                new AssistantTurnGuard()
        );
        Method stableErrorCode = AssistantChatController.class.getDeclaredMethod("stableErrorCode", Exception.class);
        stableErrorCode.setAccessible(true);

        Object result = stableErrorCode.invoke(controller, new BusinessException("MODEL_NOT_AVAILABLE"));

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("MODEL_NOT_AVAILABLE");
    }
}
