package com.dong.ddrag.modelplatform;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.service.ModelConnectionService;
import com.dong.ddrag.modelplatform.service.ModelTestService;
import com.dong.ddrag.modelplatform.service.ModelTestService.ModelTestExecutionPort;
import com.dong.ddrag.modelplatform.service.ModelTestService.TestExecutionResult;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderAdapter;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import com.dong.ddrag.modelplatform.provider.ProviderConnectionSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConnectionServiceTest {
    private ModelConnectionMapper connectionMapper;
    private ModelConnectionModelMapper modelMapper;
    private ChatModelProviderRegistry providerRegistry;
    private ChatModelProviderAdapter providerAdapter;
    private ModelConnectionService service;

    @BeforeEach
    void setUp() {
        connectionMapper = mock(ModelConnectionMapper.class);
        modelMapper = mock(ModelConnectionModelMapper.class);
        providerRegistry = mock(ChatModelProviderRegistry.class);
        providerAdapter = mock(ChatModelProviderAdapter.class);
        when(providerRegistry.require(any())).thenReturn(providerAdapter);
        when(providerAdapter.connectionSchema()).thenReturn(new ProviderConnectionSchema(ProviderType.OPENAI, null, List.of()));
        service = new ModelConnectionService(connectionMapper, modelMapper, new ObjectMapper(), providerRegistry);
    }

    @Test
    void shouldCreateUserByokWithoutReturningApiKey() {
        when(connectionMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<ModelConnectionEntity>getArgument(0).setId(11L);
            return 1;
        });

        ModelConnectionService.ConnectionView result = service.createUserConnection(1001L,
                new ModelConnectionService.ConnectionCommand("OPENAI", "private", "https://api.example.com",
                        "sk-sensitive-1234", Map.of(), null));

        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.ownerType()).isEqualTo("USER");
        assertThat(result.maskedApiKey()).isEqualTo("****1234");
        assertThat(result.toString()).doesNotContain("sk-sensitive");
        ArgumentCaptor<ModelConnectionEntity> captor = ArgumentCaptor.forClass(ModelConnectionEntity.class);
        verify(connectionMapper).insert(captor.capture());
        assertThat(captor.getValue().readApiKeyPlaintext()).isEqualTo("sk-sensitive-1234");
        assertThat(captor.getValue().getCredentialStorageType()).isEqualTo("ENCRYPTED");
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(1001L);
    }

    @Test
    void shouldListPlatformModelsThroughPlatformOwnedConnection() {
        ModelConnectionEntity platform = connection(11L, "PLATFORM", null, "ACTIVE", 4L);
        ModelConnectionModelEntity model = model(21L, 11L, "PASSED", 4L, true);
        when(connectionMapper.selectOwnedById(11L, "PLATFORM", null)).thenReturn(platform);
        when(modelMapper.selectByConnectionId(11L)).thenReturn(List.of(model));

        List<ModelConnectionService.ModelView> result = service.listPlatformModels(11L);

        assertThat(result).extracting(ModelConnectionService.ModelView::modelName)
                .containsExactly("gpt-test");
        assertThat(result.getFirst().enabled()).isTrue();
        verify(connectionMapper).selectOwnedById(11L, "PLATFORM", null);
    }

    @Test
    void shouldRejectCredentialMaterialSmuggledIntoProviderOptions() {
        when(providerAdapter.connectionSchema()).thenReturn(new ProviderConnectionSchema(ProviderType.OPENAI,
                null, List.of(new com.dong.ddrag.modelplatform.provider.ProviderFieldSchema("apiKey", "password",
                true, true, null))));

        assertThatThrownBy(() -> service.createUserConnection(1001L,
                new ModelConnectionService.ConnectionCommand("OPENAI", "private", null, "real-key",
                        Map.of("apiKey", "smuggled-key"), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MODEL_CONNECTION_SENSITIVE_OPTION_UNSUPPORTED");

        verify(connectionMapper, never()).insert(any());
    }

    @Test
    void shouldIncrementConfigVersionAndInvalidateTestsWhenCriticalConfigChanges() {
        ModelConnectionEntity current = connection(11L, "USER", 1001L, "ACTIVE", 3L);
        current.setBaseUrl("https://old.example.com");
        current.setApiKeyPlaintext("old-key");
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(current);
        when(connectionMapper.updateOwnedConfig(any(), eq(3L), eq("ACTIVE"))).thenReturn(1);

        service.updateUserConnection(1001L, 11L,
                new ModelConnectionService.ConnectionCommand("OPENAI", "private", "https://new.example.com",
                        "new-key", Map.of(), null));

        ArgumentCaptor<ModelConnectionEntity> captor = ArgumentCaptor.forClass(ModelConnectionEntity.class);
        verify(connectionMapper).updateOwnedConfig(captor.capture(), eq(3L), eq("ACTIVE"));
        ModelConnectionEntity updated = captor.getValue();
        assertThat(updated.getConfigVersion()).isEqualTo(4L);
        assertThat(updated.getStatus()).isEqualTo("UNVERIFIED");
        assertThat(updated.getLastConnectionTestStatus()).isEqualTo("UNVERIFIED");
        verify(modelMapper).invalidateTests(eq(11L), any(LocalDateTime.class));
    }

    @Test
    void shouldRejectEnablingModelUntilCurrentVersionTestPassed() {
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "UNVERIFIED", 4L);
        ModelConnectionModelEntity model = model(21L, 11L, "FAILED", 4L, false);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(modelMapper.selectById(21L, 11L)).thenReturn(model);

        assertThatThrownBy(() -> service.setUserModelEnabled(1001L, 11L, 21L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MODEL_NOT_TESTED");
        verify(modelMapper, never()).updateEnabled(any(), any(), any(), any(), any());
    }

    @Test
    void shouldHideOnlyTheOwnedModelAndDisableIt() {
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "ACTIVE", 4L);
        ModelConnectionModelEntity model = model(21L, 11L, "PASSED", 4L, true);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(modelMapper.selectById(21L, 11L)).thenReturn(model);
        when(modelMapper.hideById(eq(21L), eq(11L), any())).thenReturn(1);

        service.hideUserModel(1001L, 11L, 21L);

        verify(modelMapper).hideById(eq(21L), eq(11L), any());
        assertThat(model.getEnabled()).isFalse();
    }

    @Test
    void shouldIgnoreStaleTestCompletionAndApplyCurrentVersionWithCas() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "UNVERIFIED", 7L);
        ModelConnectionModelEntity model = model(21L, 11L, "UNVERIFIED", null, false);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(modelMapper.selectById(21L, 11L)).thenReturn(model);
        when(port.testModel(any())).thenReturn(new TestExecutionResult(true, null));
        when(modelMapper.completeTestCas(eq(21L), eq(11L), eq(7L), eq("PASSED"), any())).thenReturn(0);

        ModelTestService.TestOutcome stale = testService.testUserModel(1001L, 11L, 21L);
        assertThat(stale.applied()).isFalse();

        when(modelMapper.completeTestCas(eq(21L), eq(11L), eq(7L), eq("PASSED"), any())).thenReturn(1);
        ModelTestService.TestOutcome applied = testService.testUserModel(1001L, 11L, 21L);
        assertThat(applied.applied()).isTrue();
        assertThat(applied.configVersion()).isEqualTo(7L);
    }

    @Test
    void shouldRunBatchModelTestsInRequestOrder() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "UNVERIFIED", 7L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(modelMapper.selectById(21L, 11L)).thenReturn(model(21L, 11L, "UNVERIFIED", null, false));
        when(modelMapper.selectById(22L, 11L)).thenReturn(model(22L, 11L, "UNVERIFIED", null, false));
        when(port.testModel(any())).thenReturn(new TestExecutionResult(true, null));
        when(modelMapper.completeTestCas(any(), any(), any(), any(), any())).thenReturn(1);

        List<ModelTestService.TestOutcome> outcomes =
                testService.testUserModelsSequentially(1001L, 11L, List.of(21L, 22L));

        assertThat(outcomes).extracting(ModelTestService.TestOutcome::modelId).containsExactly(21L, 22L);
        InOrder order = inOrder(port);
        order.verify(port).testModel(org.mockito.ArgumentMatchers.argThat(target -> target.modelId().equals(21L)));
        order.verify(port).testModel(org.mockito.ArgumentMatchers.argThat(target -> target.modelId().equals(22L)));
    }

    @Test
    void shouldRejectDirectActivationOutsideTestAndEnableFlow() {
        ModelConnectionEntity current = connection(11L, "USER", 1001L, "UNVERIFIED", 1L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(current);

        assertThatThrownBy(() -> service.changeUserStatus(1001L, 11L, "ACTIVE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ILLEGAL_STATE_TRANSITION");
    }

    @Test
    void shouldRequireDeleteOperationSoApiKeyCleanupCannotBeBypassed() {
        ModelConnectionEntity current = connection(11L, "USER", 1001L, "ACTIVE", 1L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(current);

        assertThatThrownBy(() -> service.changeUserStatus(1001L, 11L, "DELETED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DELETE_REQUIRES_DELETE_OPERATION");
        verify(connectionMapper, never()).updateOwnedStatus(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotReactivateDisabledConnectionByEnablingModel() {
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "DISABLED", 4L);
        ModelConnectionModelEntity model = model(21L, 11L, "PASSED", 4L, false);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(modelMapper.selectById(21L, 11L)).thenReturn(model);

        assertThatThrownBy(() -> service.setUserModelEnabled(1001L, 11L, 21L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MODEL_CONNECTION_DISABLED");
        verify(modelMapper, never()).updateEnabled(any(), any(), any(), any(), any());
    }

    @Test
    void shouldUseCurrentConfigVersionWhenEnablingAndActivatingModel() {
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "UNVERIFIED", 4L);
        ModelConnectionModelEntity model = model(21L, 11L, "PASSED", 4L, false);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(modelMapper.selectById(21L, 11L)).thenReturn(model);
        when(modelMapper.updateEnabled(eq(21L), eq(11L), eq(4L), eq(true), any())).thenReturn(1);
        when(connectionMapper.updateOwnedStatus(eq(11L), eq("USER"), eq(1001L), eq("UNVERIFIED"),
                eq(4L), eq("ACTIVE"), any())).thenReturn(1);

        ModelConnectionService.ModelView result = service.setUserModelEnabled(1001L, 11L, 21L, true);

        assertThat(result.enabled()).isTrue();
        verify(modelMapper).updateEnabled(eq(21L), eq(11L), eq(4L), eq(true), any());
        verify(connectionMapper).updateOwnedStatus(eq(11L), eq("USER"), eq(1001L), eq("UNVERIFIED"),
                eq(4L), eq("ACTIVE"), any());
    }

    @Test
    void shouldMoveFailedConnectionBackToUnverifiedAfterSuccessfulConnectionTest() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "FAILED", 7L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(port.testConnection(any())).thenReturn(new TestExecutionResult(true, null));
        when(connectionMapper.completeConnectionTestCas(eq(11L), eq(7L), eq("PASSED"),
                eq("UNVERIFIED"), any())).thenReturn(1);

        ModelTestService.TestOutcome outcome = testService.testUserConnection(1001L, 11L);

        assertThat(outcome.applied()).isTrue();
        verify(connectionMapper).completeConnectionTestCas(eq(11L), eq(7L), eq("PASSED"),
                eq("UNVERIFIED"), any());
    }

    @Test
    void shouldRestoreOnlyModelsRediscoveredBySuccessfulConnectionTest() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "ACTIVE", 7L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(port.testConnection(any())).thenReturn(new TestExecutionResult(true, null, List.of("gpt-restored")));
        when(connectionMapper.completeConnectionTestCas(eq(11L), eq(7L), eq("PASSED"),
                eq("ACTIVE"), any())).thenReturn(1);

        ModelTestService.TestOutcome outcome = testService.testUserConnection(1001L, 11L);

        assertThat(outcome.applied()).isTrue();
        verify(modelMapper).upsertCatalogModel(org.mockito.ArgumentMatchers.argThat(model ->
                model.getConnectionId().equals(11L) && model.getModelName().equals("gpt-restored")));
    }

    @Test
    void shouldRefreshDiscoveredModelsWithoutUpdatingConnectionTestStatus() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "ACTIVE", 7L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(port.testConnection(any())).thenReturn(
                new TestExecutionResult(true, null, List.of("gpt-restored", "gpt-new")));

        ModelTestService.CatalogRefreshOutcome outcome = testService.refreshUserModels(1001L, 11L);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.discoveredCount()).isEqualTo(2);
        verify(modelMapper, org.mockito.Mockito.times(2)).upsertCatalogModel(any());
        verify(connectionMapper, never()).completeConnectionTestCas(any(), any(), any(), any(), any());
    }

    @Test
    void shouldKeepActiveConnectionAvailableWhenCatalogRefreshFails() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "ACTIVE", 7L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(port.testConnection(any())).thenReturn(new TestExecutionResult(false, "PROVIDER_ERROR"));
        when(connectionMapper.completeConnectionTestCas(eq(11L), eq(7L), eq("FAILED"),
                eq("ACTIVE"), any())).thenReturn(1);

        ModelTestService.TestOutcome outcome = testService.testUserConnection(1001L, 11L);

        assertThat(outcome.applied()).isTrue();
        verify(connectionMapper).completeConnectionTestCas(eq(11L), eq(7L), eq("FAILED"),
                eq("ACTIVE"), any());
    }

    @Test
    void shouldReactivateFailedConnectionWhenAPreviouslyVerifiedModelRemainsEnabled() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "FAILED", 7L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(modelMapper.selectByConnectionId(11L))
                .thenReturn(List.of(model(21L, 11L, "PASSED", 7L, true)));
        when(port.testConnection(any())).thenReturn(new TestExecutionResult(false, "PROVIDER_ERROR"));
        when(connectionMapper.completeConnectionTestCas(eq(11L), eq(7L), eq("FAILED"),
                eq("ACTIVE"), any())).thenReturn(1);

        ModelTestService.TestOutcome outcome = testService.testUserConnection(1001L, 11L);

        assertThat(outcome.applied()).isTrue();
        verify(connectionMapper).completeConnectionTestCas(eq(11L), eq(7L), eq("FAILED"),
                eq("ACTIVE"), any());
    }

    @Test
    void shouldNotMergeDiscoveredModelsFromStaleConnectionTest() {
        ModelTestExecutionPort port = mock(ModelTestExecutionPort.class);
        ModelTestService testService = new ModelTestService(connectionMapper, modelMapper, port);
        ModelConnectionEntity connection = connection(11L, "USER", 1001L, "UNVERIFIED", 7L);
        when(connectionMapper.selectOwnedById(11L, "USER", 1001L)).thenReturn(connection);
        when(port.testConnection(any())).thenReturn(
                new TestExecutionResult(true, null, List.of("stale-provider-model")));
        when(connectionMapper.completeConnectionTestCas(eq(11L), eq(7L), eq("PASSED"),
                eq("UNVERIFIED"), any())).thenReturn(0);

        ModelTestService.TestOutcome outcome = testService.testUserConnection(1001L, 11L);

        assertThat(outcome.applied()).isFalse();
        verify(modelMapper, never()).upsertCatalogModel(any());
    }

    private static ModelConnectionEntity connection(Long id, String ownerType, Long ownerUserId,
                                                     String status, Long version) {
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setId(id);
        entity.setProviderType("OPENAI");
        entity.setOwnerType(ownerType);
        entity.setOwnerUserId(ownerUserId);
        entity.setName("private");
        entity.setApiKeyPlaintext("secret");
        entity.setMaskedKeySuffix("cret");
        entity.setStatus(status);
        entity.setConfigVersion(version);
        return entity;
    }

    private static ModelConnectionModelEntity model(Long id, Long connectionId, String status,
                                                     Long testedVersion, boolean enabled) {
        ModelConnectionModelEntity entity = new ModelConnectionModelEntity();
        entity.setId(id);
        entity.setConnectionId(connectionId);
        entity.setModelName("gpt-test");
        entity.setSourceType("MANUAL");
        entity.setTestStatus(status);
        entity.setTestedConfigVersion(testedVersion);
        entity.setEnabled(enabled);
        return entity;
    }
}
