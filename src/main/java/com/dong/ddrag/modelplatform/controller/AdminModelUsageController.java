package com.dong.ddrag.modelplatform.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.service.ModelUsageQueryService;
import com.dong.ddrag.modelplatform.service.ModelUsageQueryService.UsageFilter;
import com.dong.ddrag.modelplatform.service.ModelUsageQueryService.UsageReport;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-usage")
public class AdminModelUsageController {
    private final CurrentUserService currentUserService;
    private final ModelUsageQueryService queryService;

    public AdminModelUsageController(CurrentUserService currentUserService, ModelUsageQueryService queryService) {
        this.currentUserService = currentUserService;
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<UsageReport> usage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String logicalStatus,
            @RequestParam(required = false) String transportStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endedAt,
            HttpServletRequest request) {
        currentUserService.requireSystemAdmin(request);
        UsageFilter filter = new UsageFilter(userId, providerType, modelName, scenario, logicalStatus,
                transportStatus, startedAt, endedAt);
        return ApiResponse.success(queryService.queryAdminUsage(filter));
    }
}
