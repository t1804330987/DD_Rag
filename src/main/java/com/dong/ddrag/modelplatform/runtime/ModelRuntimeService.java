package com.dong.ddrag.modelplatform.runtime;

import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ModelTestStatus;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService;
import com.dong.ddrag.modelplatform.service.ModelScenarioRouteService;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ModelRuntimeService {
    private final AssistantSessionMapper sessionMapper;
    private final ModelConnectionMapper connectionMapper;
    private final ModelConnectionModelMapper modelMapper;
    private final ModelAuthorizationService authorizationService;
    private final ModelScenarioRouteService routeService;

    public ModelRuntimeService(AssistantSessionMapper sessionMapper, ModelConnectionMapper connectionMapper,
                               ModelConnectionModelMapper modelMapper,
                               ModelAuthorizationService authorizationService,
                               ModelScenarioRouteService routeService) {
        this.sessionMapper = sessionMapper;
        this.connectionMapper = connectionMapper;
        this.modelMapper = modelMapper;
        this.authorizationService = authorizationService;
        this.routeService = routeService;
    }

    public ModelInvocationContext resolveAssistant(Long userId, Long sessionId,
                                                   InvocationCorrelation correlation) {
        AssistantSessionEntity session = sessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException("ASSISTANT_SESSION_NOT_FOUND");
        }
        if (session.getCurrentModelConnectionId() == null || session.getCurrentModelId() == null) {
            throw new BusinessException("MODEL_NOT_CONFIGURED");
        }
        return resolve(userId, ModelScenario.ASSISTANT_CHAT, session.getCurrentModelConnectionId(),
                session.getCurrentModelId(), sessionId, correlation, false);
    }

    /** Validates a session model choice against the same current rules used at invocation time. */
    public void requireAvailableAssistantModel(Long userId, Long connectionId, Long modelId) {
        resolve(userId, ModelScenario.ASSISTANT_CHAT, connectionId, modelId, null,
                new InvocationCorrelation("assistant-selection", "assistant-selection", null, null, null),
                false);
    }

    public ModelInvocationContext resolveScenario(Long userId, ModelScenario scenario,
                                                  InvocationCorrelation correlation) {
        if (scenario == null || scenario == ModelScenario.ASSISTANT_CHAT) {
            throw new BusinessException("MODEL_SCENARIO_INVALID");
        }
        try {
            ModelScenarioRouteService.RouteView route = routeService.resolve(scenario.name());
            return resolve(userId, scenario, route.connectionId(), route.modelId(),
                    correlation == null ? null : correlation.sessionId(), correlation, true);
        } catch (BusinessException exception) {
            if (shouldFallbackToAssistantSessionModel(scenario, correlation, exception)) {
                return resolveAssistantSessionModel(userId, scenario, correlation);
            }
            throw exception;
        }
    }

    public ResolvedModel requireCurrent(ModelInvocationContext context) {
        if (context == null) {
            throw new BusinessException("MODEL_INVOCATION_CONTEXT_INVALID");
        }
        if (context.scenario() == ModelScenario.CONNECTION_TEST || context.scenario() == ModelScenario.MODEL_TEST) {
            return requireCurrentTestTarget(context);
        }
        ModelConnectionEntity connection = connectionMapper.selectFormalById(context.connectionId());
        if (connection == null) {
            throw new BusinessException("MODEL_CONNECTION_NOT_ACTIVE");
        }
        ModelConnectionModelEntity model = modelMapper.selectById(context.modelId(), context.connectionId());
        if (!isCurrentAvailableModel(model, connection.getConfigVersion())) {
            throw new BusinessException("MODEL_NOT_AVAILABLE");
        }
        if (!Objects.equals(context.configVersion(), connection.getConfigVersion())) {
            throw new BusinessException("MODEL_CONFIGURATION_CHANGED");
        }
        if (!authorizationService.isAuthorized(context.userId(), connection)) {
            throw new BusinessException("MODEL_NOT_AUTHORIZED");
        }
        return new ResolvedModel(context, connection, model);
    }

    /**
     * Test calls may exercise an unverified connection/model, but never a deleted/disabled
     * target or a target whose ownership/configuration has changed after the test started.
     */
    private ResolvedModel requireCurrentTestTarget(ModelInvocationContext context) {
        Long ownerUserId = context.ownerType() == ConnectionOwnerType.USER ? context.userId() : null;
        ModelConnectionEntity connection = connectionMapper.selectOwnedById(context.connectionId(),
                context.ownerType().name(), ownerUserId);
        if (connection == null || "DISABLED".equals(connection.getStatus())) {
            throw new BusinessException("MODEL_CONNECTION_NOT_ACTIVE");
        }
        if (!Objects.equals(context.configVersion(), connection.getConfigVersion())) {
            throw new BusinessException("MODEL_CONFIGURATION_CHANGED");
        }
        if (context.scenario() == ModelScenario.CONNECTION_TEST) {
            return new ResolvedModel(context, connection, null);
        }
        ModelConnectionModelEntity model = modelMapper.selectById(context.modelId(), context.connectionId());
        if (model == null || !Objects.equals(model.getConnectionId(), context.connectionId())) {
            throw new BusinessException("MODEL_NOT_FOUND");
        }
        return new ResolvedModel(context, connection, model);
    }

    private ModelInvocationContext resolveAssistantSessionModel(
            Long userId,
            ModelScenario scenario,
            InvocationCorrelation correlation
    ) {
        AssistantSessionEntity session = sessionMapper.selectByIdAndUserId(correlation.sessionId(), userId);
        if (session == null) {
            throw new BusinessException("ASSISTANT_SESSION_NOT_FOUND");
        }
        if (session.getCurrentModelConnectionId() == null || session.getCurrentModelId() == null) {
            throw new BusinessException("MODEL_NOT_CONFIGURED");
        }
        // SESSION_SUMMARY 是 Assistant 会话的内部收尾任务；未配置专用路由时复用当前会话模型，
        // 但仍然走 resolve(...) 的可用性、授权和配置版本校验，避免静默绕过模型治理。
        return resolve(userId, scenario, session.getCurrentModelConnectionId(), session.getCurrentModelId(),
                correlation.sessionId(), correlation, false);
    }

    private boolean shouldFallbackToAssistantSessionModel(
            ModelScenario scenario,
            InvocationCorrelation correlation,
            BusinessException exception
    ) {
        return scenario == ModelScenario.SESSION_SUMMARY
                && correlation != null
                && correlation.sessionId() != null
                && "MODEL_NOT_CONFIGURED".equals(exception.getMessage());
    }

    private ModelInvocationContext resolve(Long userId, ModelScenario scenario, Long connectionId, Long modelId,
                                           Long sessionId, InvocationCorrelation correlation,
                                           boolean requirePlatformConnection) {
        if (userId == null || correlation == null) {
            throw new BusinessException("MODEL_INVOCATION_CONTEXT_INVALID");
        }
        ModelConnectionEntity connection = connectionMapper.selectFormalById(connectionId);
        if (connection == null) {
            throw new BusinessException("MODEL_CONNECTION_NOT_ACTIVE");
        }
        if (requirePlatformConnection
                && scenario != ModelScenario.ASSISTANT_CHAT
                && !ConnectionOwnerType.PLATFORM.name().equals(connection.getOwnerType())) {
            throw new BusinessException("MODEL_ROUTE_PLATFORM_CONNECTION_REQUIRED");
        }
        ModelConnectionModelEntity model = modelMapper.selectById(modelId, connectionId);
        if (!isCurrentAvailableModel(model, connection.getConfigVersion())) {
            throw new BusinessException("MODEL_NOT_AVAILABLE");
        }
        if (!authorizationService.isAuthorized(userId, connection)) {
            throw new BusinessException("MODEL_NOT_AUTHORIZED");
        }
        return new ModelInvocationContext(userId, scenario, connectionId, modelId,
                connection.getConfigVersion(), parseProvider(connection.getProviderType()), model.getModelName(),
                connection.getName(), parseOwner(connection.getOwnerType()), sessionId,
                correlation.userMessageId(), correlation.assistantMessageId(),
                correlation.turnId(), correlation.requestId());
    }

    private static boolean isCurrentAvailableModel(ModelConnectionModelEntity model, Long configVersion) {
        return model != null && Boolean.TRUE.equals(model.getEnabled())
                && ModelTestStatus.PASSED.name().equals(model.getTestStatus())
                && Objects.equals(model.getTestedConfigVersion(), configVersion);
    }

    private static ProviderType parseProvider(String value) {
        try {
            return ProviderType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("MODEL_PROVIDER_UNSUPPORTED");
        }
    }

    private static ConnectionOwnerType parseOwner(String value) {
        try {
            return ConnectionOwnerType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("MODEL_CONNECTION_OWNER_INVALID");
        }
    }

    public record InvocationCorrelation(String turnId, String requestId, Long userMessageId,
                                        Long assistantMessageId, Long sessionId) {
        public InvocationCorrelation(String turnId, String requestId, Long userMessageId,
                                     Long assistantMessageId) {
            this(turnId, requestId, userMessageId, assistantMessageId, null);
        }
    }

    public record ResolvedModel(ModelInvocationContext context, ModelConnectionEntity connection,
                                ModelConnectionModelEntity model) { }
}
