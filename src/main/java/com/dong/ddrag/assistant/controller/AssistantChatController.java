package com.dong.ddrag.assistant.controller;

import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatResponse;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.dong.ddrag.assistant.config.AssistantStreamExecutor;
import com.dong.ddrag.assistant.service.AssistantService;
import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.concurrency.AssistantTurnGuard;
import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.modelplatform.runtime.ModelCallCancellation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.RejectedExecutionException;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/assistant")
public class AssistantChatController {

    private static final long SSE_TIMEOUT_MILLIS = 0L;

    private final AssistantService assistantService;
    private final AssistantStreamExecutor streamExecutor;
    private final AssistantTurnGuard turnGuard;
    private final ModelRuntimeProperties runtimeProperties;

    public AssistantChatController(AssistantService assistantService,
                                   AssistantStreamExecutor streamExecutor,
                                   AssistantTurnGuard turnGuard) {
        this(assistantService, streamExecutor, turnGuard, new ModelRuntimeProperties());
    }

    @Autowired
    public AssistantChatController(AssistantService assistantService,
                                   AssistantStreamExecutor streamExecutor,
                                   AssistantTurnGuard turnGuard,
                                   ModelRuntimeProperties runtimeProperties) {
        this.assistantService = assistantService;
        this.streamExecutor = streamExecutor;
        this.turnGuard = turnGuard;
        this.runtimeProperties = runtimeProperties;
    }

    @PostMapping("/chat")
    public ApiResponse<AssistantChatResponse> chat(
            @Valid @RequestBody AssistantChatRequest requestBody,
            HttpServletRequest request
    ) {
        // Existing sessions can be admitted before any user message is persisted. New sessions
        // receive their id during request preparation and are guarded by AssistantService.
        AssistantTurnGuard.TurnPermit turnPermit = requestBody.sessionId() == null
                ? AssistantTurnGuard.noOp()
                : turnGuard.acquire(requestBody.sessionId());
        try {
            return ApiResponse.success(assistantService.chat(request, requestBody));
        } finally {
            turnPermit.close();
        }
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamChat(
            @Valid @RequestBody AssistantChatRequest requestBody,
            HttpServletRequest request
    ) {
        assistantService.requireBusinessStreamUser(request);
        // A new-session request has no persisted session key until the service creates it.
        // The request-id record still prevents duplicate work during that initial admission.
        AssistantTurnGuard.TurnPermit turnPermit = requestBody.sessionId() == null
                ? AssistantTurnGuard.noOp()
                : turnGuard.acquire(requestBody.sessionId());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean completedByServer = new AtomicBoolean(false);
        AtomicReference<Long> streamSessionId = new AtomicReference<>(requestBody.sessionId());
        ModelCallCancellation cancellation = new ModelCallCancellation();
        Runnable cancel = () -> {
            cancellation.request();
            turnPermit.close();
        };
        emitter.onCompletion(() -> {
            closed.set(true);
            if (!completedByServer.get()) {
                cancel.run();
            }
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            if (!completedByServer.get()) {
                cancel.run();
            }
        });
        emitter.onError(error -> {
            closed.set(true);
            if (!completedByServer.get()) {
                cancel.run();
            }
        });
        Disposable businessDeadline = Schedulers.parallel().schedule(() -> {
            if (!completedByServer.compareAndSet(false, true)) {
                return;
            }
            cancellation.requestBusinessTimeout();
            closed.set(true);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(AssistantChatStreamEvent.error(
                                requestBody.sessionId(), requestBody.toolMode(), requestBody.groupId(), "CALL_TIMEOUT")));
                emitter.complete();
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            } finally {
                turnPermit.close();
            }
        }, runtimeProperties.getTimeout().getBusinessTotal().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        try {
            // Submit before returning the emitter so an exhausted bounded executor is a real HTTP 429.
            streamExecutor.submit(cancellation, () -> {
            try {
                assistantService.streamChat(request, requestBody, event -> {
                    if ("start".equals(event.event()) && event.sessionId() != null) {
                        streamSessionId.set(event.sessionId());
                    }
                    try {
                        sendEvent(emitter, event, closed);
                    } catch (IOException exception) {
                        cancellation.request();
                        throw new IllegalStateException("发送 SSE 事件失败", exception);
                    }
                }, cancellation);
                completeEmitter(emitter, closed, completedByServer);
            } catch (Exception exception) {
                if (cancellation.isRequested() || isCancellation(exception)) {
                    completeEmitter(emitter, closed, completedByServer);
                    return;
                }
                try {
                    sendEvent(emitter, AssistantChatStreamEvent.error(
                            streamSessionId.get(),
                            requestBody.toolMode(),
                            requestBody.groupId(),
                            stableErrorCode(exception)
                    ), closed);
                    completeEmitter(emitter, closed, completedByServer);
                } catch (IOException ioException) {
                    cancellation.request();
                    completeEmitterWithError(emitter, closed, completedByServer, ioException);
                }
            } finally {
                businessDeadline.dispose();
                turnPermit.close();
            }
            });
        } catch (RejectedExecutionException exception) {
            businessDeadline.dispose();
            turnPermit.close();
            throw new BusinessException("GLOBAL_BUSY");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    private boolean isCancellation(Exception exception) {
        return exception instanceof BusinessException businessException
                && "CALL_CANCELLED".equals(businessException.getMessage());
    }

    private String stableErrorCode(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            String code = businessException.getMessage();
            return switch (code) {
                case "GLOBAL_BUSY", "USER_BUSY", "CONNECTION_BUSY", "FIRST_TOKEN_TIMEOUT",
                     "STREAM_IDLE_TIMEOUT", "CALL_TIMEOUT", "PROVIDER_RATE_LIMITED",
                     "MODEL_NOT_CONFIGURED", "MODEL_NOT_AVAILABLE", "MODEL_NOT_AUTHORIZED",
                     "MODEL_CONFIGURATION_CHANGED", "MODEL_CONNECTION_NOT_ACTIVE" -> code;
                default -> "PROVIDER_ERROR";
            };
        }
        return "PROVIDER_ERROR";
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

    private void completeEmitter(SseEmitter emitter, AtomicBoolean closed, AtomicBoolean completedByServer) {
        if (closed.compareAndSet(false, true)) {
            completedByServer.set(true);
            emitter.complete();
        }
    }

    private void completeEmitterWithError(
            SseEmitter emitter,
            AtomicBoolean closed,
            AtomicBoolean completedByServer,
            Exception exception
    ) {
        if (closed.compareAndSet(false, true)) {
            completedByServer.set(true);
            emitter.completeWithError(exception);
        }
    }
}
