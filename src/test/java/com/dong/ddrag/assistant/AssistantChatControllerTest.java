package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.controller.AssistantChatController;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.dong.ddrag.assistant.service.AssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class AssistantChatControllerTest {

    @Test
    void shouldIgnoreSendAndCompleteWhenEmitterAlreadyClosed() throws Exception {
        AssistantService assistantService = mock(AssistantService.class);
        AssistantChatController controller = new AssistantChatController(assistantService);
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
                closed
        )).doesNotThrowAnyException();
    }
}
