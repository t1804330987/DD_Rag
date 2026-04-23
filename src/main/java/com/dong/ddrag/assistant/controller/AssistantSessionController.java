package com.dong.ddrag.assistant.controller;

import com.dong.ddrag.assistant.model.dto.session.CreateAssistantSessionRequest;
import com.dong.ddrag.assistant.model.dto.session.UpdateAssistantSessionRequest;
import com.dong.ddrag.assistant.model.vo.session.AssistantSessionDetailVO;
import com.dong.ddrag.assistant.model.vo.session.AssistantSessionListItemVO;
import com.dong.ddrag.assistant.service.AssistantSessionService;
import com.dong.ddrag.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assistant/sessions")
public class AssistantSessionController {

    private final AssistantSessionService assistantSessionService;

    public AssistantSessionController(AssistantSessionService assistantSessionService) {
        this.assistantSessionService = assistantSessionService;
    }

    @PostMapping
    public ApiResponse<AssistantSessionDetailVO> createSession(
            @RequestBody(required = false) CreateAssistantSessionRequest requestBody,
            HttpServletRequest request
    ) {
        return ApiResponse.success(assistantSessionService.createSession(request));
    }

    @GetMapping
    public List<AssistantSessionListItemVO> listSessions(HttpServletRequest request) {
        return assistantSessionService.listSessions(request);
    }

    @GetMapping("/{sessionId}")
    public AssistantSessionDetailVO getSessionDetail(
            @PathVariable Long sessionId,
            HttpServletRequest request
    ) {
        return assistantSessionService.getSessionDetail(request, sessionId);
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<AssistantSessionDetailVO> updateSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateAssistantSessionRequest requestBody,
            HttpServletRequest request
    ) {
        return ApiResponse.success(assistantSessionService.renameSession(request, sessionId, requestBody));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @PathVariable Long sessionId,
            HttpServletRequest request
    ) {
        assistantSessionService.deleteSession(request, sessionId);
        return ApiResponse.success(null);
    }
}
