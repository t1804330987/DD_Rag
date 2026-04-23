package com.dong.ddrag.qa;

import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.qa.model.dto.AskQuestionRequest;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import com.dong.ddrag.qa.service.QaChatService;
import com.dong.ddrag.qa.service.QaService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaServiceTest {

    @Test
    void shouldRequireReadablePermissionBeforeAsking() {
        GroupMembershipService groupMembershipService = mock(GroupMembershipService.class);
        QaChatService qaChatService = mock(QaChatService.class);
        QaService qaService = new QaService(groupMembershipService, qaChatService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AskQuestionRequest askQuestionRequest = new AskQuestionRequest();
        askQuestionRequest.setGroupId(2001L);
        askQuestionRequest.setQuestion("产品团队如何安排迭代？");
        AskQuestionResponse expected = new AskQuestionResponse(
                true,
                "根据当前资料，产品团队每两周发布一次。",
                null,
                null,
                java.util.List.of()
        );
        when(qaChatService.ask(2001L, "产品团队如何安排迭代？")).thenReturn(expected);

        AskQuestionResponse actual = qaService.ask(request, askQuestionRequest);

        assertThat(actual).isEqualTo(expected);
        verify(groupMembershipService).requireGroupReadable(request, 2001L);
        verify(qaChatService).ask(2001L, "产品团队如何安排迭代？");
    }
}
