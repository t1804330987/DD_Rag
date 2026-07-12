package com.dong.ddrag.modelplatform.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService.GrantReplacementCommand;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService.GrantView;
import com.dong.ddrag.modelplatform.service.ModelScenarioRouteService;
import com.dong.ddrag.modelplatform.service.ModelScenarioRouteService.RouteView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-governance")
public class AdminModelGovernanceController {
    private final CurrentUserService currentUserService;
    private final ModelAuthorizationService authorizationService;
    private final ModelScenarioRouteService routeService;

    public AdminModelGovernanceController(CurrentUserService currentUserService,
                                          ModelAuthorizationService authorizationService,
                                          ModelScenarioRouteService routeService) {
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.routeService = routeService;
    }

    @PutMapping("/connections/{connectionId}/grants")
    public ApiResponse<GrantView> replaceGrants(@PathVariable Long connectionId,
                                                @RequestBody GrantReplacementCommand command,
                                                HttpServletRequest request) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(authorizationService.replacePlatformGrants(connectionId, command));
    }

    @GetMapping("/connections/{connectionId}/grants")
    public ApiResponse<GrantView> getGrants(@PathVariable Long connectionId, HttpServletRequest request) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(authorizationService.getPlatformGrants(connectionId));
    }

    @PutMapping("/routes/{scenario}")
    public ApiResponse<RouteView> bindRoute(@PathVariable String scenario, @RequestBody RouteCommand command,
                                            HttpServletRequest request) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(routeService.bind(scenario, command.connectionId(), command.modelId()));
    }

    @GetMapping("/routes/{scenario}")
    public ApiResponse<RouteView> getRoute(@PathVariable String scenario, HttpServletRequest request) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(routeService.resolve(scenario));
    }

    public record RouteCommand(Long connectionId, Long modelId) { }
}
