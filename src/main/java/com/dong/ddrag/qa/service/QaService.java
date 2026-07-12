package com.dong.ddrag.qa.service;

import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.qa.model.dto.AskQuestionRequest;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class QaService {

    private final GroupMembershipService groupMembershipService;
    private final QaChatService qaChatService;
    private final CurrentUserService currentUserService;

    public QaService(
            GroupMembershipService groupMembershipService,
            QaChatService qaChatService,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipService = groupMembershipService;
        this.qaChatService = qaChatService;
        this.currentUserService = currentUserService;
    }

    public AskQuestionResponse ask(HttpServletRequest request, AskQuestionRequest askQuestionRequest) {
        Long groupId = askQuestionRequest.getGroupId();
        groupMembershipService.requireGroupReadable(request, groupId);
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return qaChatService.ask(currentUser.userId(), groupId, askQuestionRequest.getQuestion());
    }
}
