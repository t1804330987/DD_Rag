package com.dong.ddrag.modelplatform;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.exception.ForbiddenException;
import com.dong.ddrag.common.exception.GlobalExceptionHandler;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.controller.AdminModelGovernanceController;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService;
import com.dong.ddrag.modelplatform.service.ModelScenarioRouteService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminModelGovernanceControllerTest {
    private CurrentUserService currentUserService;
    private ModelAuthorizationService authorizationService;
    private ModelScenarioRouteService routeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        authorizationService = mock(ModelAuthorizationService.class);
        routeService = mock(ModelScenarioRouteService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AdminModelGovernanceController(currentUserService, authorizationService, routeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRequireSystemAdminForGovernanceEndpoints() throws Exception {
        when(currentUserService.requireSystemAdmin(any())).thenThrow(new ForbiddenException("当前用户不是系统管理员"));

        mvc.perform(put("/api/admin/model-governance/connections/11/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allBusinessUsers\":true,\"userIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReplaceGrantsWithoutReturningConnectionSecrets() throws Exception {
        when(currentUserService.requireSystemAdmin(any())).thenReturn(
                new CurrentUserService.CurrentUser(1L, "admin", "Admin", SystemRole.ADMIN, false));
        when(authorizationService.replacePlatformGrants(any(), any())).thenReturn(
                new ModelAuthorizationService.GrantView(11L, true, List.of()));

        mvc.perform(put("/api/admin/model-governance/connections/11/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allBusinessUsers\":true,\"userIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionId").value(11))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("apiKey"))));

        verify(authorizationService).replacePlatformGrants(any(), any());
    }

    @Test
    void shouldReadCurrentGrantsWithoutReturningConnectionSecrets() throws Exception {
        when(currentUserService.requireSystemAdmin(any())).thenReturn(
                new CurrentUserService.CurrentUser(1L, "admin", "Admin", SystemRole.ADMIN, false));
        when(authorizationService.getPlatformGrants(11L)).thenReturn(
                new ModelAuthorizationService.GrantView(11L, false, List.of(1001L)));

        mvc.perform(get("/api/admin/model-governance/connections/11/grants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userIds[0]").value(1001))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("apiKey"))));

        verify(authorizationService).getPlatformGrants(11L);
    }
}
