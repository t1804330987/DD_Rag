package com.dong.ddrag.assistant.controller;

import com.dong.ddrag.assistant.model.vo.conversation.AssistantConversationContextVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant/sessions")
public class AssistantConversationController {

    private final AssistantConversationService assistantConversationService;

    public AssistantConversationController(AssistantConversationService assistantConversationService) {
        this.assistantConversationService = assistantConversationService;
    }

    @GetMapping("/{sessionId}/context")
    public AssistantConversationContextVO getConversationContext(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "12") int recentLimit,
            HttpServletRequest request
    ) {
        return assistantConversationService.getConversationContext(request, sessionId, recentLimit);
    }
}
