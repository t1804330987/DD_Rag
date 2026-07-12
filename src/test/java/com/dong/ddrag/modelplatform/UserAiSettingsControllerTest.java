package com.dong.ddrag.modelplatform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.controller.UserAiSettingsController;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import com.dong.ddrag.modelplatform.provider.ProviderConnectionSchema;
import com.dong.ddrag.modelplatform.provider.ProviderFieldSchema;
import com.dong.ddrag.modelplatform.service.AssistantInstructionProfileService;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserAiSettingsControllerTest {
    private CurrentUserService currentUserService;
    private AssistantInstructionProfileService instructionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        instructionService = mock(AssistantInstructionProfileService.class);
        ChatModelProviderRegistry providerRegistry = mock(ChatModelProviderRegistry.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserAiSettingsController(
                currentUserService, providerRegistry, instructionService)).build();
        when(currentUserService.requireBusinessUser(any())).thenReturn(
                new CurrentUserService.CurrentUser(1001L, "u1001", "User", SystemRole.USER, false));
    }

    @Test
    void instructionProfilesAreAlwaysScopedToCurrentBusinessUser() throws Exception {
        when(instructionService.list(1001L)).thenReturn(List.of());

        mvc.perform(get("/api/ai-settings/instruction-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(currentUserService).requireBusinessUser(any());
        verify(instructionService).list(1001L);
    }

    @Test
    void creatingInstructionProfileUsesOnlyCurrentBusinessUser() throws Exception {
        mvc.perform(post("/api/ai-settings/instruction-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"专注\",\"content\":\"回答简洁\",\"makeDefault\":true}"))
                .andExpect(status().isOk());

        verify(instructionService).create(1001L, "专注", "回答简洁", true);
    }

    @Test
    void providerSchemasMustNotExposeSensitiveDefaultValues() throws Exception {
        ChatModelProviderRegistry registry = mock(ChatModelProviderRegistry.class);
        when(registry.connectionSchemas()).thenReturn(List.of(new ProviderConnectionSchema(ProviderType.OPENAI,
                "https://api.example.test", List.of(new ProviderFieldSchema("apiKey", "password", true,
                true, "must-not-leak")))));
        MockMvc schemaMvc = MockMvcBuilders.standaloneSetup(new UserAiSettingsController(
                currentUserService, registry, instructionService)).build();

        schemaMvc.perform(get("/api/ai-settings/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fields[0].defaultValue").doesNotExist());
    }
}
