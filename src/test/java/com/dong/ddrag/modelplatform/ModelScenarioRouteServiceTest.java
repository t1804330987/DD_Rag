package com.dong.ddrag.modelplatform;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.mapper.ModelScenarioRouteMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelScenarioRouteEntity;
import com.dong.ddrag.modelplatform.service.ModelScenarioRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelScenarioRouteServiceTest {
    private ModelConnectionMapper connectionMapper;
    private ModelConnectionModelMapper modelMapper;
    private ModelScenarioRouteMapper routeMapper;
    private ModelScenarioRouteService service;

    @BeforeEach
    void setUp() {
        connectionMapper = mock(ModelConnectionMapper.class);
        modelMapper = mock(ModelConnectionModelMapper.class);
        routeMapper = mock(ModelScenarioRouteMapper.class);
        service = new ModelScenarioRouteService(connectionMapper, modelMapper, routeMapper);
    }

    @Test
    void shouldBindOnlyActivePlatformEnabledCurrentTestedModel() {
        when(connectionMapper.selectFormalById(11L)).thenReturn(connection("PLATFORM", "ACTIVE", 7L));
        when(modelMapper.selectById(21L, 11L)).thenReturn(model(true, "PASSED", 7L));
        when(routeMapper.upsert(any())).thenReturn(1);

        ModelScenarioRouteService.RouteView result = service.bind("QUERY_PLANNING", 11L, 21L);

        assertThat(result.scenario()).isEqualTo("QUERY_PLANNING");
        ArgumentCaptor<ModelScenarioRouteEntity> captor = ArgumentCaptor.forClass(ModelScenarioRouteEntity.class);
        verify(routeMapper).upsert(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    void shouldRejectByokConnectionForPlatformScenario() {
        when(connectionMapper.selectFormalById(11L)).thenReturn(connection("USER", "ACTIVE", 7L));
        assertThatThrownBy(() -> service.bind("QUERY_PLANNING", 11L, 21L))
                .isInstanceOf(BusinessException.class).hasMessage("MODEL_ROUTE_PLATFORM_CONNECTION_REQUIRED");
        verify(routeMapper, never()).upsert(any());
    }

    @Test
    void shouldRejectDisabledUntestedOrStaleModel() {
        when(connectionMapper.selectFormalById(11L)).thenReturn(connection("PLATFORM", "ACTIVE", 7L));

        when(modelMapper.selectById(21L, 11L)).thenReturn(model(false, "PASSED", 7L));
        assertThatThrownBy(() -> service.bind("QUERY_PLANNING", 11L, 21L))
                .hasMessage("MODEL_ROUTE_MODEL_UNAVAILABLE");

        when(modelMapper.selectById(21L, 11L)).thenReturn(model(true, "FAILED", 7L));
        assertThatThrownBy(() -> service.bind("QUERY_PLANNING", 11L, 21L))
                .hasMessage("MODEL_ROUTE_MODEL_UNAVAILABLE");

        when(modelMapper.selectById(21L, 11L)).thenReturn(model(true, "PASSED", 6L));
        assertThatThrownBy(() -> service.bind("QUERY_PLANNING", 11L, 21L))
                .hasMessage("MODEL_ROUTE_MODEL_UNAVAILABLE");
    }

    @Test
    void shouldReturnStableErrorWhenScenarioRouteMissingWithoutYamlFallback() {
        when(routeMapper.selectFormalRouteByScenario("SESSION_SUMMARY")).thenReturn(null);
        assertThatThrownBy(() -> service.resolve("SESSION_SUMMARY"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("MODEL_NOT_CONFIGURED");
        verify(connectionMapper, never()).selectFormalById(any());
    }

    private static ModelConnectionEntity connection(String ownerType, String status, Long configVersion) {
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setId(11L);
        entity.setOwnerType(ownerType);
        entity.setStatus(status);
        entity.setConfigVersion(configVersion);
        return entity;
    }

    private static ModelConnectionModelEntity model(boolean enabled, String testStatus, Long testedVersion) {
        ModelConnectionModelEntity entity = new ModelConnectionModelEntity();
        entity.setId(21L);
        entity.setConnectionId(11L);
        entity.setEnabled(enabled);
        entity.setTestStatus(testStatus);
        entity.setTestedConfigVersion(testedVersion);
        return entity;
    }
}
