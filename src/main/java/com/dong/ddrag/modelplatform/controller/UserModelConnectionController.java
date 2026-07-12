package com.dong.ddrag.modelplatform.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.service.ModelConnectionService;
import com.dong.ddrag.modelplatform.service.ModelConnectionService.ConnectionCommand;
import com.dong.ddrag.modelplatform.service.ModelConnectionService.ConnectionView;
import com.dong.ddrag.modelplatform.service.ModelConnectionService.ModelView;
import com.dong.ddrag.modelplatform.service.ModelTestService;
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

@RestController
@RequestMapping("/api/ai-settings/model-connections")
public class UserModelConnectionController {
    private final CurrentUserService currentUserService;
    private final ModelConnectionService connectionService;
    private final ModelTestService testService;

    public UserModelConnectionController(CurrentUserService currentUserService,
                                         ModelConnectionService connectionService, ModelTestService testService) {
        this.currentUserService = currentUserService;
        this.connectionService = connectionService;
        this.testService = testService;
    }

    @GetMapping public ApiResponse<List<ConnectionView>> list(HttpServletRequest request) {
        return ApiResponse.success(connectionService.listUserConnections(userId(request)));
    }
    @GetMapping("/{connectionId}") public ApiResponse<ConnectionView> get(@PathVariable Long connectionId,
                                                                          HttpServletRequest request) {
        return ApiResponse.success(connectionService.getUserConnection(userId(request), connectionId));
    }
    @GetMapping("/{connectionId}/models") public ApiResponse<List<ModelView>> listModels(
            @PathVariable Long connectionId, HttpServletRequest request) {
        return ApiResponse.success(connectionService.listUserModels(userId(request), connectionId));
    }
    @PostMapping public ApiResponse<ConnectionView> create(@RequestBody ConnectionCommand command,
                                                            HttpServletRequest request) {
        return ApiResponse.success(connectionService.createUserConnection(userId(request), command));
    }
    @PutMapping("/{connectionId}") public ApiResponse<ConnectionView> update(@PathVariable Long connectionId,
            @RequestBody ConnectionCommand command, HttpServletRequest request) {
        return ApiResponse.success(connectionService.updateUserConnection(userId(request), connectionId, command));
    }
    @DeleteMapping("/{connectionId}") public ApiResponse<Void> delete(@PathVariable Long connectionId,
                                                                        HttpServletRequest request) {
        connectionService.deleteUserConnection(userId(request), connectionId);
        return ApiResponse.success(null);
    }
    @PatchMapping("/{connectionId}/status/{status}") public ApiResponse<ConnectionView> status(
            @PathVariable Long connectionId, @PathVariable String status, HttpServletRequest request) {
        return ApiResponse.success(connectionService.changeUserStatus(userId(request), connectionId, status));
    }
    @PostMapping("/{connectionId}/catalog") public ApiResponse<List<ModelView>> mergeCatalog(
            @PathVariable Long connectionId, @RequestBody CatalogCommand command, HttpServletRequest request) {
        Long userId = userId(request);
        return ApiResponse.success(connectionService.mergeCatalog(connectionId, "USER", userId,
                List.of(), command.manualModels()));
    }
    @PatchMapping("/{connectionId}/models/{modelId}/enabled/{enabled}") public ApiResponse<ModelView> enableModel(
            @PathVariable Long connectionId, @PathVariable Long modelId, @PathVariable boolean enabled,
            HttpServletRequest request) {
        return ApiResponse.success(connectionService.setUserModelEnabled(userId(request), connectionId, modelId, enabled));
    }
    @DeleteMapping("/{connectionId}/models/{modelId}") public ApiResponse<Void> hideModel(
            @PathVariable Long connectionId, @PathVariable Long modelId, HttpServletRequest request) {
        connectionService.hideUserModel(userId(request), connectionId, modelId);
        return ApiResponse.success(null);
    }
    @PostMapping("/{connectionId}/test") public ApiResponse<ModelTestService.TestOutcome> testConnection(
            @PathVariable Long connectionId, HttpServletRequest request) {
        return ApiResponse.success(testService.testUserConnection(userId(request), connectionId));
    }
    @PostMapping("/{connectionId}/models/refresh")
    public ApiResponse<ModelTestService.CatalogRefreshOutcome> refreshModels(
            @PathVariable Long connectionId, HttpServletRequest request) {
        return ApiResponse.success(testService.refreshUserModels(userId(request), connectionId));
    }
    @PostMapping("/{connectionId}/models/{modelId}/test") public ApiResponse<ModelTestService.TestOutcome> testModel(
            @PathVariable Long connectionId, @PathVariable Long modelId, HttpServletRequest request) {
        return ApiResponse.success(testService.testUserModel(userId(request), connectionId, modelId));
    }
    @PostMapping("/{connectionId}/models/test") public ApiResponse<List<ModelTestService.TestOutcome>> testModels(
            @PathVariable Long connectionId, @RequestBody ModelIdsCommand command, HttpServletRequest request) {
        return ApiResponse.success(testService.testUserModelsSequentially(userId(request), connectionId, command.modelIds()));
    }

    private Long userId(HttpServletRequest request) { return currentUserService.requireBusinessUser(request).userId(); }
    public record CatalogCommand(List<String> manualModels) { }
    public record ModelIdsCommand(List<Long> modelIds) { }
}
