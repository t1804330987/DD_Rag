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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/model-connections")
public class AdminModelConnectionController {
    private final CurrentUserService currentUserService;
    private final ModelConnectionService connectionService;
    private final ModelTestService testService;

    public AdminModelConnectionController(CurrentUserService currentUserService,
                                          ModelConnectionService connectionService, ModelTestService testService) {
        this.currentUserService = currentUserService;
        this.connectionService = connectionService;
        this.testService = testService;
    }

    @GetMapping public ApiResponse<List<ConnectionView>> list(HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.listPlatformConnections());
    }
    @GetMapping("/{connectionId}") public ApiResponse<ConnectionView> get(@PathVariable Long connectionId,
                                                                          HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.getPlatformConnection(connectionId));
    }
    @GetMapping("/{connectionId}/models") public ApiResponse<List<ModelView>> listModels(@PathVariable Long connectionId,
                                                                                             HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.listPlatformModels(connectionId));
    }
    @PostMapping public ApiResponse<ConnectionView> create(@RequestBody ConnectionCommand command,
                                                            HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.createPlatformConnection(command));
    }
    @PutMapping("/{connectionId}") public ApiResponse<ConnectionView> update(@PathVariable Long connectionId,
            @RequestBody ConnectionCommand command, HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.updatePlatformConnection(connectionId, command));
    }
    @DeleteMapping("/{connectionId}") public ApiResponse<Void> delete(@PathVariable Long connectionId,
                                                                        HttpServletRequest request) {
        requireAdmin(request); connectionService.deletePlatformConnection(connectionId); return ApiResponse.success(null);
    }
    @PatchMapping("/{connectionId}/status/{status}") public ApiResponse<ConnectionView> status(
            @PathVariable Long connectionId, @PathVariable String status, HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.changePlatformStatus(connectionId, status));
    }
    @PostMapping("/{connectionId}/catalog") public ApiResponse<List<ModelView>> mergeCatalog(
            @PathVariable Long connectionId, @RequestBody UserModelConnectionController.CatalogCommand command,
            HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.mergeCatalog(connectionId, "PLATFORM", null,
                List.of(), command.manualModels()));
    }
    @PatchMapping("/{connectionId}/models/{modelId}/enabled/{enabled}") public ApiResponse<ModelView> enableModel(
            @PathVariable Long connectionId, @PathVariable Long modelId, @PathVariable boolean enabled,
            HttpServletRequest request) {
        requireAdmin(request); return ApiResponse.success(connectionService.setPlatformModelEnabled(connectionId, modelId, enabled));
    }
    @PostMapping("/{connectionId}/test") public ApiResponse<ModelTestService.TestOutcome> testConnection(
            @PathVariable Long connectionId, HttpServletRequest request) {
        return ApiResponse.success(testService.testPlatformConnection(adminId(request), connectionId));
    }
    @PostMapping("/{connectionId}/models/{modelId}/test") public ApiResponse<ModelTestService.TestOutcome> testModel(
            @PathVariable Long connectionId, @PathVariable Long modelId, HttpServletRequest request) {
        return ApiResponse.success(testService.testPlatformModel(adminId(request), connectionId, modelId));
    }
    @PostMapping("/{connectionId}/models/test") public ApiResponse<List<ModelTestService.TestOutcome>> testModels(
            @PathVariable Long connectionId, @RequestBody UserModelConnectionController.ModelIdsCommand command,
            HttpServletRequest request) {
        return ApiResponse.success(testService.testPlatformModelsSequentially(
                adminId(request), connectionId, command.modelIds()));
    }

    private void requireAdmin(HttpServletRequest request) { currentUserService.requireSystemAdmin(request); }
    private Long adminId(HttpServletRequest request) { return currentUserService.requireSystemAdmin(request).userId(); }
}
