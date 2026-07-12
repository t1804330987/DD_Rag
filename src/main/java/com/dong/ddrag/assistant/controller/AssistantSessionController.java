package com.dong.ddrag.assistant.controller;

import com.dong.ddrag.assistant.model.dto.session.CreateAssistantSessionRequest;
import com.dong.ddrag.assistant.model.dto.session.UpdateAssistantSessionRequest;
import com.dong.ddrag.assistant.model.dto.session.SelectAssistantInstructionRequest;
import com.dong.ddrag.assistant.model.dto.session.SelectAssistantModelRequest;
import com.dong.ddrag.assistant.service.AssistantModelSelectionService;
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
    private final AssistantModelSelectionService modelSelectionService;

    public AssistantSessionController(AssistantSessionService assistantSessionService,
                                      AssistantModelSelectionService modelSelectionService) {
        this.assistantSessionService = assistantSessionService;
        this.modelSelectionService = modelSelectionService;
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

    @GetMapping("/models")
    public ApiResponse<List<AssistantModelSelectionService.AvailableModelView>> listAvailableModels(
            HttpServletRequest request
    ) {
        return ApiResponse.success(modelSelectionService.listAvailableModels(request));
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

    @PatchMapping("/{sessionId}/model")
    public ApiResponse<Void> selectModel(@PathVariable Long sessionId,
                                         @Valid @RequestBody SelectAssistantModelRequest requestBody,
                                         HttpServletRequest request) {
        assistantSessionService.selectModel(request, sessionId, requestBody.connectionId(), requestBody.modelId());
        return ApiResponse.success(null);
    }

    @PatchMapping("/{sessionId}/instruction-profile")
    public ApiResponse<Void> selectInstructionProfile(@PathVariable Long sessionId,
                                                      @Valid @RequestBody SelectAssistantInstructionRequest requestBody,
                                                      HttpServletRequest request) {
        assistantSessionService.selectInstructionProfile(request, sessionId, requestBody.instructionProfileId());
        return ApiResponse.success(null);
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
