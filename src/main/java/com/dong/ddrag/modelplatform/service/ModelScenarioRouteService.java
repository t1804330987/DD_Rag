package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.mapper.ModelScenarioRouteMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelScenarioRouteEntity;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ModelTestStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelScenarioRouteService {
    private final ModelConnectionMapper connectionMapper;
    private final ModelConnectionModelMapper modelMapper;
    private final ModelScenarioRouteMapper routeMapper;

    public ModelScenarioRouteService(ModelConnectionMapper connectionMapper, ModelConnectionModelMapper modelMapper,
                                     ModelScenarioRouteMapper routeMapper) {
        this.connectionMapper = connectionMapper;
        this.modelMapper = modelMapper;
        this.routeMapper = routeMapper;
    }

    @Transactional
    public RouteView bind(String scenarioValue, Long connectionId, Long modelId) {
        ModelScenario scenario = parseScenario(scenarioValue);
        if (connectionId == null || connectionId <= 0 || modelId == null || modelId <= 0) {
            throw new BusinessException("MODEL_ROUTE_REQUEST_INVALID");
        }
        ModelConnectionEntity connection = connectionMapper.selectFormalById(connectionId);
        if (connection == null) {
            throw new BusinessException("MODEL_ROUTE_CONNECTION_NOT_ACTIVE");
        }
        if (!ConnectionOwnerType.PLATFORM.name().equals(connection.getOwnerType())) {
            throw new BusinessException("MODEL_ROUTE_PLATFORM_CONNECTION_REQUIRED");
        }
        ModelConnectionModelEntity model = modelMapper.selectById(modelId, connectionId);
        if (!isAvailable(model, connection.getConfigVersion())) {
            throw new BusinessException("MODEL_ROUTE_MODEL_UNAVAILABLE");
        }
        LocalDateTime now = LocalDateTime.now();
        ModelScenarioRouteEntity route = new ModelScenarioRouteEntity();
        route.setScenario(scenario.name());
        route.setConnectionId(connectionId);
        route.setModelId(modelId);
        route.setEnabled(true);
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        if (routeMapper.upsert(route) != 1) {
            throw new BusinessException("MODEL_ROUTE_UPDATE_FAILED");
        }
        return view(route);
    }

    public RouteView resolve(String scenarioValue) {
        ModelScenario scenario = parseScenario(scenarioValue);
        ModelScenarioRouteEntity route = routeMapper.selectFormalRouteByScenario(scenario.name());
        if (route == null) {
            throw new BusinessException("MODEL_NOT_CONFIGURED");
        }
        return view(route);
    }

    private static boolean isAvailable(ModelConnectionModelEntity model, Long configVersion) {
        return model != null && Boolean.TRUE.equals(model.getEnabled())
                && ModelTestStatus.PASSED.name().equals(model.getTestStatus())
                && Objects.equals(model.getTestedConfigVersion(), configVersion);
    }

    private static ModelScenario parseScenario(String value) {
        try {
            return ModelScenario.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("MODEL_SCENARIO_INVALID");
        }
    }

    private static RouteView view(ModelScenarioRouteEntity route) {
        return new RouteView(route.getScenario(), route.getConnectionId(), route.getModelId(),
                Boolean.TRUE.equals(route.getEnabled()), route.getUpdatedAt());
    }

    public record RouteView(String scenario, Long connectionId, Long modelId, boolean enabled,
                            LocalDateTime updatedAt) { }
}
