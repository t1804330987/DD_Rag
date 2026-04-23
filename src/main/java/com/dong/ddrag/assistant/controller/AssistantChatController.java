package com.dong.ddrag.assistant.controller;

import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatResponse;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.dong.ddrag.assistant.service.AssistantService;
import com.dong.ddrag.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/assistant")
public class AssistantChatController {

    private static final long SSE_TIMEOUT_MILLIS = 0L;

    private final AssistantService assistantService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public AssistantChatController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public ApiResponse<AssistantChatResponse> chat(
            @Valid @RequestBody AssistantChatRequest requestBody,
            HttpServletRequest request
    ) {
        return ApiResponse.success(assistantService.chat(request, requestBody));
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @Valid @RequestBody AssistantChatRequest requestBody,
            HttpServletRequest request
    ) {
        // 流式对话入口只负责建立 SSE 通道，并把具体编排下沉到 AssistantService。
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        sseExecutor.execute(() -> {
            try {
                assistantService.streamChat(request, requestBody, event -> {
                    try {
                        sendEvent(emitter, event, closed);
                    } catch (IOException exception) {
                        throw new IllegalStateException("发送 SSE 事件失败", exception);
                    }
                });
                completeEmitter(emitter, closed);
            } catch (Exception exception) {
                try {
                    sendEvent(emitter, AssistantChatStreamEvent.error(
                            requestBody.sessionId(),
                            requestBody.toolMode(),
                            requestBody.groupId(),
                            exception.getMessage()
                    ), closed);
                    completeEmitter(emitter, closed);
                } catch (IOException ioException) {
                    completeEmitterWithError(emitter, closed, ioException);
                }
            }
        });
        return emitter;
    }

    private void sendEvent(
            SseEmitter emitter,
            AssistantChatStreamEvent event,
            AtomicBoolean closed
    ) throws IOException {
        // 客户端断开后不再继续写事件，避免把模型侧异常放大成二次 SSE 错误。
        if (closed.get()) {
            return;
        }
        emitter.send(SseEmitter.event()
                .name(event.event())
                .data(event));
    }

    private void completeEmitter(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private void completeEmitterWithError(
            SseEmitter emitter,
            AtomicBoolean closed,
            Exception exception
    ) {
        if (closed.compareAndSet(false, true)) {
            emitter.completeWithError(exception);
        }
    }
}
