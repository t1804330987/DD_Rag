package com.dong.ddrag.modelplatform;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.controller.AdminModelConnectionController;
import com.dong.ddrag.modelplatform.controller.UserModelConnectionController;
import com.dong.ddrag.modelplatform.service.ModelConnectionService;
import com.dong.ddrag.modelplatform.service.ModelTestService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelConnectionControllerTest {
    private CurrentUserService currentUserService;
    private ModelConnectionService connectionService;
    private ModelTestService testService;
    private MockMvc userMvc;
    private MockMvc adminMvc;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        connectionService = mock(ModelConnectionService.class);
        testService = mock(ModelTestService.class);
        userMvc = MockMvcBuilders.standaloneSetup(
                new UserModelConnectionController(currentUserService, connectionService, testService)).build();
        adminMvc = MockMvcBuilders.standaloneSetup(
                new AdminModelConnectionController(currentUserService, connectionService, testService)).build();
    }

    @Test
    void userEndpointMustRequireBusinessUserAndOnlyListOwnedByok() throws Exception {
        when(currentUserService.requireBusinessUser(any())).thenReturn(
                new CurrentUserService.CurrentUser(1001L, "u1001", "User", SystemRole.USER, false));
        when(connectionService.listUserConnections(1001L)).thenReturn(List.of());

        userMvc.perform(get("/api/ai-settings/model-connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(currentUserService).requireBusinessUser(any());
        verify(connectionService).listUserConnections(1001L);
    }

    @Test
    void userModelCatalogMustBeResolvedThroughOwnedConnection() throws Exception {
        when(currentUserService.requireBusinessUser(any())).thenReturn(
                new CurrentUserService.CurrentUser(1001L, "u1001", "User", SystemRole.USER, false));
        when(connectionService.listUserModels(1001L, 55L)).thenReturn(List.of());

        userMvc.perform(get("/api/ai-settings/model-connections/55/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(connectionService).listUserModels(1001L, 55L);
    }

    @Test
    void userCanHideOnlyTheirOwnCatalogModel() throws Exception {
        when(currentUserService.requireBusinessUser(any())).thenReturn(
                new CurrentUserService.CurrentUser(1001L, "u1001", "User", SystemRole.USER, false));

        userMvc.perform(delete("/api/ai-settings/model-connections/55/models/66"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(connectionService).hideUserModel(1001L, 55L, 66L);
    }

    @Test
    void userCanRefreshModelsWithoutRunningConnectionTest() throws Exception {
        when(currentUserService.requireBusinessUser(any())).thenReturn(
                new CurrentUserService.CurrentUser(1001L, "u1001", "User", SystemRole.USER, false));
        when(testService.refreshUserModels(1001L, 55L)).thenReturn(
                new ModelTestService.CatalogRefreshOutcome(55L, 3L, true, null, 2));

        userMvc.perform(post("/api/ai-settings/model-connections/55/models/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.discoveredCount").value(2));

        verify(testService).refreshUserModels(1001L, 55L);
    }

    @Test
    void adminEndpointMustRequireSystemAdminAndOnlyListPlatformConnections() throws Exception {
        when(currentUserService.requireSystemAdmin(any())).thenReturn(
                new CurrentUserService.CurrentUser(1L, "admin", "Admin", SystemRole.ADMIN, false));
        when(connectionService.listPlatformConnections()).thenReturn(List.of());

        adminMvc.perform(get("/api/admin/model-connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(currentUserService).requireSystemAdmin(any());
        verify(connectionService).listPlatformConnections();
    }

    @Test
    void adminModelCatalogMustRequireSystemAdminAndOnlyListPlatformModels() throws Exception {
        when(currentUserService.requireSystemAdmin(any())).thenReturn(
                new CurrentUserService.CurrentUser(1L, "admin", "Admin", SystemRole.ADMIN, false));
        when(connectionService.listPlatformModels(55L)).thenReturn(List.of());

        adminMvc.perform(get("/api/admin/model-connections/55/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(currentUserService).requireSystemAdmin(any());
        verify(connectionService).listPlatformModels(55L);
    }
}
