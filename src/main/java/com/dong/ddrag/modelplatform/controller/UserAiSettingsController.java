package com.dong.ddrag.modelplatform.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import com.dong.ddrag.modelplatform.provider.ProviderConnectionSchema;
import com.dong.ddrag.modelplatform.provider.ProviderFieldSchema;
import com.dong.ddrag.modelplatform.service.AssistantInstructionProfileService;
import com.dong.ddrag.modelplatform.service.AssistantInstructionProfileService.ProfileView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-facing AI settings API. Every endpoint resolves the authenticated business user. */
@RestController
@RequestMapping("/api/ai-settings")
public class UserAiSettingsController {
    private final CurrentUserService currentUserService;
    private final ChatModelProviderRegistry providerRegistry;
    private final AssistantInstructionProfileService instructionService;

    public UserAiSettingsController(CurrentUserService currentUserService,
                                    ChatModelProviderRegistry providerRegistry,
                                    AssistantInstructionProfileService instructionService) {
        this.currentUserService = currentUserService;
        this.providerRegistry = providerRegistry;
        this.instructionService = instructionService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<ProviderSchemaView>> providers(HttpServletRequest request) {
        userId(request);
        return ApiResponse.success(providerRegistry.connectionSchemas().stream().map(ProviderSchemaView::from).toList());
    }

    @GetMapping("/instruction-profiles")
    public ApiResponse<List<ProfileView>> listProfiles(HttpServletRequest request) {
        return ApiResponse.success(instructionService.list(userId(request)));
    }

    @PostMapping("/instruction-profiles")
    public ApiResponse<ProfileView> createProfile(@RequestBody ProfileCommand command, HttpServletRequest request) {
        return ApiResponse.success(instructionService.create(userId(request), command.name(), command.content(), command.makeDefault()));
    }

    @PutMapping("/instruction-profiles/{profileId}")
    public ApiResponse<ProfileView> updateProfile(@PathVariable Long profileId, @RequestBody ProfileCommand command,
                                                   HttpServletRequest request) {
        return ApiResponse.success(instructionService.update(userId(request), profileId,
                command.name(), command.content(), command.makeDefault()));
    }

    @PostMapping("/instruction-profiles/{profileId}/copy")
    public ApiResponse<ProfileView> copyProfile(@PathVariable Long profileId, @RequestBody CopyProfileCommand command,
                                                 HttpServletRequest request) {
        return ApiResponse.success(instructionService.copy(userId(request), profileId, command.name(), command.makeDefault()));
    }

    @PatchMapping("/instruction-profiles/{profileId}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long profileId, HttpServletRequest request) {
        instructionService.setDefault(userId(request), profileId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/instruction-profiles/{profileId}/enabled/{enabled}")
    public ApiResponse<Void> setEnabled(@PathVariable Long profileId, @PathVariable boolean enabled,
                                        HttpServletRequest request) {
        instructionService.setEnabled(userId(request), profileId, enabled);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/instruction-profiles/{profileId}")
    public ApiResponse<Void> deleteProfile(@PathVariable Long profileId, HttpServletRequest request) {
        instructionService.delete(userId(request), profileId);
        return ApiResponse.success(null);
    }

    private Long userId(HttpServletRequest request) {
        return currentUserService.requireBusinessUser(request).userId();
    }

    public record ProfileCommand(String name, String content, boolean makeDefault) { }
    public record CopyProfileCommand(String name, boolean makeDefault) { }
    public record ProviderSchemaView(String providerType, String defaultBaseUrl, List<ProviderFieldView> fields) {
        static ProviderSchemaView from(ProviderConnectionSchema schema) {
            return new ProviderSchemaView(schema.providerType().name(), schema.defaultBaseUrl(),
                    schema.fields().stream().map(ProviderFieldView::from).toList());
        }
    }
    public record ProviderFieldView(String name, String type, boolean required, boolean sensitive, String defaultValue) {
        static ProviderFieldView from(ProviderFieldSchema schema) {
            return new ProviderFieldView(schema.name(), schema.type(), schema.required(), schema.sensitive(),
                    schema.sensitive() ? null : schema.defaultValue());
        }
    }
}
