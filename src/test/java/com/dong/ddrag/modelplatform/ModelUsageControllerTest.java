package com.dong.ddrag.modelplatform;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.exception.ForbiddenException;
import com.dong.ddrag.common.exception.GlobalExceptionHandler;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.controller.AdminModelUsageController;
import com.dong.ddrag.modelplatform.controller.ModelUsageController;
import com.dong.ddrag.modelplatform.service.ModelUsageQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelUsageControllerTest {
    private CurrentUserService currentUserService;
    private ModelUsageQueryService queryService;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        queryService = mock(ModelUsageQueryService.class);
    }

    @Test
    void shouldForceBusinessUserUsageQueryToCurrentUser() throws Exception {
        when(currentUserService.requireBusinessUser(any())).thenReturn(
                new CurrentUserService.CurrentUser(1001L, "user", "User", SystemRole.USER, false));
        when(queryService.queryUserUsage(eq(1001L), any())).thenReturn(report(1001L));
        MockMvc mvc = mvc(new ModelUsageController(currentUserService, queryService));

        mvc.perform(get("/api/ai-settings/model-usage")
                        .param("userId", "9999")
                        .param("startedAt", "2026-07-01T00:00:00")
                        .param("endedAt", "2026-08-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1001));

        verify(queryService).queryUserUsage(eq(1001L), any());
    }

    @Test
    void shouldRejectAdminFromBusinessUsageEndpoint() throws Exception {
        when(currentUserService.requireBusinessUser(any())).thenThrow(new ForbiddenException("系统管理员不能访问普通业务区"));

        mvc(new ModelUsageController(currentUserService, queryService))
                .perform(get("/api/ai-settings/model-usage"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminToFilterGlobalUsage() throws Exception {
        when(currentUserService.requireSystemAdmin(any())).thenReturn(
                new CurrentUserService.CurrentUser(1L, "admin", "Admin", SystemRole.ADMIN, false));
        when(queryService.queryAdminUsage(any())).thenReturn(report(null));
        MockMvc mvc = mvc(new AdminModelUsageController(currentUserService, queryService));

        mvc.perform(get("/api/admin/model-usage")
                        .param("userId", "1001")
                        .param("providerType", "OPENAI")
                        .param("modelName", "gpt-test")
                        .param("scenario", "ASSISTANT_CHAT")
                        .param("logicalStatus", "SUCCEEDED")
                        .param("startedAt", "2026-07-01T00:00:00")
                        .param("endedAt", "2026-08-01T00:00:00"))
                .andExpect(status().isOk());

        verify(queryService).queryAdminUsage(any());
    }

    @Test
    void shouldRejectBusinessUserFromAdminUsageEndpoint() throws Exception {
        when(currentUserService.requireSystemAdmin(any())).thenThrow(new ForbiddenException("当前用户不是系统管理员"));

        mvc(new AdminModelUsageController(currentUserService, queryService))
                .perform(get("/api/admin/model-usage"))
                .andExpect(status().isForbidden());
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ModelUsageQueryService.UsageReport report(Long userId) {
        return new ModelUsageQueryService.UsageReport(userId, 2, 10, 20, 30, 250,
                List.of(new ModelUsageQueryService.UsageGroup("OPENAI", "gpt-test", "ASSISTANT_CHAT",
                        "SUCCEEDED", "TERMINATED", 2, 10, 20, 30, 250)));
    }
}
