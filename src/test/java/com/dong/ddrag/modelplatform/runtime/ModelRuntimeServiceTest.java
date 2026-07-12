package com.dong.ddrag.modelplatform.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ConnectionStatus;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ModelTestStatus;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService;
import com.dong.ddrag.modelplatform.service.ModelScenarioRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelRuntimeServiceTest {
    @Mock private AssistantSessionMapper sessionMapper;
    @Mock private ModelConnectionMapper connectionMapper;
    @Mock private ModelConnectionModelMapper modelMapper;
    @Mock private ModelAuthorizationService authorizationService;
    @Mock private ModelScenarioRouteService routeService;

    private ModelRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new ModelRuntimeService(sessionMapper, connectionMapper, modelMapper,
                authorizationService, routeService);
    }

    @Test
    void resolvesAssistantModelFromOwnedSession() {
        AssistantSessionEntity session = session(7L, 11L, 21L, 31L);
        ModelConnectionEntity connection = connection(21L, 4L, ConnectionOwnerType.USER);
        ModelConnectionModelEntity model = model(31L, 21L, 4L);
        when(sessionMapper.selectByIdAndUserId(11L, 7L)).thenReturn(session);
        when(connectionMapper.selectFormalById(21L)).thenReturn(connection);
        when(modelMapper.selectById(31L, 21L)).thenReturn(model);
        when(authorizationService.isAuthorized(7L, connection)).thenReturn(true);

        ModelInvocationContext context = service.resolveAssistant(7L, 11L,
                new ModelRuntimeService.InvocationCorrelation("turn-1", "request-1", null, null));

        assertEquals(ModelScenario.ASSISTANT_CHAT, context.scenario());
        assertEquals(21L, context.connectionId());
        assertEquals(31L, context.modelId());
        assertEquals("qwen-plus", context.modelName());
    }

    @Test
    void resolvesInternalModelFromScenarioRoute() {
        ModelConnectionEntity connection = connection(22L, 5L, ConnectionOwnerType.PLATFORM);
        ModelConnectionModelEntity model = model(32L, 22L, 5L);
        when(routeService.resolve(ModelScenario.QUERY_PLANNING.name()))
                .thenReturn(new ModelScenarioRouteService.RouteView(ModelScenario.QUERY_PLANNING.name(),
                        22L, 32L, true, null));
        when(connectionMapper.selectFormalById(22L)).thenReturn(connection);
        when(modelMapper.selectById(32L, 22L)).thenReturn(model);
        when(authorizationService.isAuthorized(7L, connection)).thenReturn(true);

        ModelInvocationContext context = service.resolveScenario(7L, ModelScenario.QUERY_PLANNING,
                new ModelRuntimeService.InvocationCorrelation("turn-2", "request-2", 11L, 12L));

        assertEquals(ModelScenario.QUERY_PLANNING, context.scenario());
        assertEquals(22L, context.connectionId());
    }

    @Test
    void fallsBackSessionSummaryToCurrentAssistantModelWhenRouteMissing() {
        AssistantSessionEntity session = session(7L, 11L, 21L, 31L);
        ModelConnectionEntity connection = connection(21L, 4L, ConnectionOwnerType.USER);
        ModelConnectionModelEntity model = model(31L, 21L, 4L);
        when(routeService.resolve(ModelScenario.SESSION_SUMMARY.name()))
                .thenThrow(new BusinessException("MODEL_NOT_CONFIGURED"));
        when(sessionMapper.selectByIdAndUserId(11L, 7L)).thenReturn(session);
        when(connectionMapper.selectFormalById(21L)).thenReturn(connection);
        when(modelMapper.selectById(31L, 21L)).thenReturn(model);
        when(authorizationService.isAuthorized(7L, connection)).thenReturn(true);

        ModelInvocationContext context = service.resolveScenario(7L, ModelScenario.SESSION_SUMMARY,
                new ModelRuntimeService.InvocationCorrelation("turn-3", "request-3", 101L, 102L, 11L));

        assertEquals(ModelScenario.SESSION_SUMMARY, context.scenario());
        assertEquals(21L, context.connectionId());
        assertEquals(31L, context.modelId());
        assertEquals(11L, context.sessionId());
        assertEquals(ConnectionOwnerType.USER, context.ownerType());
    }

    @Test
    void rejectsSessionSummaryFallbackWhenSessionHasNoModel() {
        when(routeService.resolve(ModelScenario.SESSION_SUMMARY.name()))
                .thenThrow(new BusinessException("MODEL_NOT_CONFIGURED"));
        when(sessionMapper.selectByIdAndUserId(11L, 7L)).thenReturn(session(7L, 11L, null, null));

        assertBusinessCode("MODEL_NOT_CONFIGURED", () -> service.resolveScenario(7L,
                ModelScenario.SESSION_SUMMARY,
                new ModelRuntimeService.InvocationCorrelation("turn-3", "request-3", 101L, 102L, 11L)));
    }

    @Test
    void rejectsOtherInternalScenarioWhenRouteMissing() {
        when(routeService.resolve(ModelScenario.QUERY_PLANNING.name()))
                .thenThrow(new BusinessException("MODEL_NOT_CONFIGURED"));

        assertBusinessCode("MODEL_NOT_CONFIGURED", () -> service.resolveScenario(7L,
                ModelScenario.QUERY_PLANNING,
                new ModelRuntimeService.InvocationCorrelation("turn-2", "request-2", 11L, 12L, 11L)));
    }

    @Test
    void rejectsInternalScenarioUsingUserOwnedConnection() {
        ModelConnectionEntity connection = connection(22L, 5L, ConnectionOwnerType.USER);
        when(routeService.resolve(ModelScenario.QUERY_PLANNING.name()))
                .thenReturn(new ModelScenarioRouteService.RouteView(ModelScenario.QUERY_PLANNING.name(),
                        22L, 32L, true, null));
        when(connectionMapper.selectFormalById(22L)).thenReturn(connection);

        assertBusinessCode("MODEL_ROUTE_PLATFORM_CONNECTION_REQUIRED", () -> service.resolveScenario(7L,
                ModelScenario.QUERY_PLANNING,
                new ModelRuntimeService.InvocationCorrelation("turn-2", "request-2", 11L, 12L)));
    }

    @Test
    void rejectsRevokedAuthorization() {
        AssistantSessionEntity session = session(7L, 11L, 21L, 31L);
        ModelConnectionEntity connection = connection(21L, 4L, ConnectionOwnerType.PLATFORM);
        when(sessionMapper.selectByIdAndUserId(11L, 7L)).thenReturn(session);
        when(connectionMapper.selectFormalById(21L)).thenReturn(connection);
        when(modelMapper.selectById(31L, 21L)).thenReturn(model(31L, 21L, 4L));
        when(authorizationService.isAuthorized(7L, connection)).thenReturn(false);

        assertBusinessCode("MODEL_NOT_AUTHORIZED", () -> service.resolveAssistant(7L, 11L,
                new ModelRuntimeService.InvocationCorrelation("turn", "request", null, null)));
    }

    @Test
    void rejectsDisabledConnection() {
        AssistantSessionEntity session = session(7L, 11L, 21L, 31L);
        when(sessionMapper.selectByIdAndUserId(11L, 7L)).thenReturn(session);
        when(connectionMapper.selectFormalById(21L)).thenReturn(null);

        assertBusinessCode("MODEL_CONNECTION_NOT_ACTIVE", () -> service.resolveAssistant(7L, 11L,
                new ModelRuntimeService.InvocationCorrelation("turn", "request", null, null)));
    }

    @Test
    void rejectsStaleModelTestVersion() {
        AssistantSessionEntity session = session(7L, 11L, 21L, 31L);
        ModelConnectionEntity connection = connection(21L, 4L, ConnectionOwnerType.USER);
        when(sessionMapper.selectByIdAndUserId(11L, 7L)).thenReturn(session);
        when(connectionMapper.selectFormalById(21L)).thenReturn(connection);
        when(modelMapper.selectById(31L, 21L)).thenReturn(model(31L, 21L, 3L));

        assertBusinessCode("MODEL_NOT_AVAILABLE", () -> service.resolveAssistant(7L, 11L,
                new ModelRuntimeService.InvocationCorrelation("turn", "request", null, null)));
    }

    @Test
    void rejectsSessionWithoutConfiguredModel() {
        when(sessionMapper.selectByIdAndUserId(11L, 7L)).thenReturn(session(7L, 11L, null, null));

        assertBusinessCode("MODEL_NOT_CONFIGURED", () -> service.resolveAssistant(7L, 11L,
                new ModelRuntimeService.InvocationCorrelation("turn", "request", null, null)));
    }

    @Test
    void revalidationRejectsAuthorizationRevokedAfterContextWasCreated() {
        ModelInvocationContext context = new ModelInvocationContext(7L, ModelScenario.ASSISTANT_CHAT,
                21L, 31L, 4L, ProviderType.DASHSCOPE, "qwen-plus", "primary",
                ConnectionOwnerType.PLATFORM, 11L, null, null, "turn", "request");
        ModelConnectionEntity connection = connection(21L, 4L, ConnectionOwnerType.PLATFORM);
        when(connectionMapper.selectFormalById(21L)).thenReturn(connection);
        when(modelMapper.selectById(31L, 21L)).thenReturn(model(31L, 21L, 4L));
        when(authorizationService.isAuthorized(7L, connection)).thenReturn(false);

        assertBusinessCode("MODEL_NOT_AUTHORIZED", () -> service.requireCurrent(context));
    }

    @Test
    void revalidationRejectsChangedConfigurationVersion() {
        ModelInvocationContext context = new ModelInvocationContext(7L, ModelScenario.ASSISTANT_CHAT,
                21L, 31L, 4L, ProviderType.DASHSCOPE, "qwen-plus", "primary",
                ConnectionOwnerType.USER, 11L, null, null, "turn", "request");
        ModelConnectionEntity connection = connection(21L, 5L, ConnectionOwnerType.USER);
        when(connectionMapper.selectFormalById(21L)).thenReturn(connection);
        when(modelMapper.selectById(31L, 21L)).thenReturn(model(31L, 21L, 5L));

        assertBusinessCode("MODEL_CONFIGURATION_CHANGED", () -> service.requireCurrent(context));
    }

    @Test
    void permitsOnlyTestScenariosToUseAnUnverifiedOwnedModel() {
        ModelInvocationContext context = new ModelInvocationContext(7L, ModelScenario.MODEL_TEST,
                21L, 31L, 4L, ProviderType.DASHSCOPE, "qwen-plus", "private",
                ConnectionOwnerType.USER, null, null, null, null, null);
        ModelConnectionEntity connection = connection(21L, 4L, ConnectionOwnerType.USER);
        connection.setStatus(ConnectionStatus.UNVERIFIED.name());
        ModelConnectionModelEntity model = model(31L, 21L, null);
        model.setEnabled(false);
        model.setTestStatus(ModelTestStatus.UNVERIFIED.name());
        when(connectionMapper.selectOwnedById(21L, ConnectionOwnerType.USER.name(), 7L)).thenReturn(connection);
        when(modelMapper.selectById(31L, 21L)).thenReturn(model);

        assertEquals(model, service.requireCurrent(context).model());
    }

    @Test
    void permitsConnectionProbeWithoutInventingAModelId() {
        ModelInvocationContext context = new ModelInvocationContext(7L, ModelScenario.CONNECTION_TEST,
                21L, null, 4L, ProviderType.DASHSCOPE, "connection-test", "private",
                ConnectionOwnerType.USER, null, null, null, null, null);
        ModelConnectionEntity connection = connection(21L, 4L, ConnectionOwnerType.USER);
        connection.setStatus(ConnectionStatus.UNVERIFIED.name());
        when(connectionMapper.selectOwnedById(21L, ConnectionOwnerType.USER.name(), 7L)).thenReturn(connection);

        assertEquals(null, service.requireCurrent(context).model());
    }

    private static AssistantSessionEntity session(Long userId, Long id, Long connectionId, Long modelId) {
        AssistantSessionEntity entity = new AssistantSessionEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setCurrentModelConnectionId(connectionId);
        entity.setCurrentModelId(modelId);
        return entity;
    }

    private static ModelConnectionEntity connection(Long id, Long configVersion, ConnectionOwnerType ownerType) {
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setId(id);
        entity.setConfigVersion(configVersion);
        entity.setStatus(ConnectionStatus.ACTIVE.name());
        entity.setProviderType(ProviderType.DASHSCOPE.name());
        entity.setOwnerType(ownerType.name());
        entity.setOwnerUserId(ownerType == ConnectionOwnerType.USER ? 7L : null);
        entity.setName("primary");
        return entity;
    }

    private static ModelConnectionModelEntity model(Long id, Long connectionId, Long testedVersion) {
        ModelConnectionModelEntity entity = new ModelConnectionModelEntity();
        entity.setId(id);
        entity.setConnectionId(connectionId);
        entity.setModelName("qwen-plus");
        entity.setEnabled(true);
        entity.setTestStatus(ModelTestStatus.PASSED.name());
        entity.setTestedConfigVersion(testedVersion);
        return entity;
    }

    private static void assertBusinessCode(String code, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(code, exception.getMessage());
    }
}
