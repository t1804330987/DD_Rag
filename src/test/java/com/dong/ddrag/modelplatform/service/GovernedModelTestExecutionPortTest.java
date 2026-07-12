package com.dong.ddrag.modelplatform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderAdapter;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import com.dong.ddrag.modelplatform.provider.DiscoveredModel;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;

class GovernedModelTestExecutionPortTest {
    @Test
    void runsUnverifiedModelTestThroughTheExecutor() {
        ModelConnectionMapper connections = mock(ModelConnectionMapper.class);
        ModelConnectionModelMapper models = mock(ModelConnectionModelMapper.class);
        ModelInvocationExecutor executor = mock(ModelInvocationExecutor.class);
        ChatModelProviderRegistry registry = mock(ChatModelProviderRegistry.class);
        ModelConnectionEntity connection = connection();
        ModelConnectionModelEntity model = model();
        when(connections.selectOwnedById(11L, "USER", 7L)).thenReturn(connection);
        when(models.selectById(21L, 11L)).thenReturn(model);
        when(executor.call(any(), any())).thenReturn(mock(ChatResponse.class));
        GovernedModelTestExecutionPort port = new GovernedModelTestExecutionPort(connections, models,
                executor, registry, new ObjectMapper());

        ModelTestService.TestExecutionResult result = port.testModel(target());

        assertThat(result.success()).isTrue();
        ArgumentCaptor<com.dong.ddrag.modelplatform.runtime.ModelInvocationContext> context =
                ArgumentCaptor.forClass(com.dong.ddrag.modelplatform.runtime.ModelInvocationContext.class);
        verify(executor).call(context.capture(), any());
        assertThat(context.getValue().scenario()).isEqualTo(ModelScenario.MODEL_TEST);
        assertThat(context.getValue().configVersion()).isEqualTo(1L);
        assertThat(context.getValue().modelId()).isEqualTo(21L);
    }

    @Test
    void runsConnectionDiscoveryThroughTheExecutorInsteadOfTheService() {
        ModelConnectionMapper connections = mock(ModelConnectionMapper.class);
        ModelConnectionModelMapper models = mock(ModelConnectionModelMapper.class);
        ModelInvocationExecutor executor = mock(ModelInvocationExecutor.class);
        ChatModelProviderRegistry registry = mock(ChatModelProviderRegistry.class);
        ChatModelProviderAdapter adapter = mock(ChatModelProviderAdapter.class);
        when(connections.selectOwnedById(11L, "USER", 7L)).thenReturn(connection());
        when(registry.require(any())).thenReturn(adapter);
        when(adapter.discoverModels(any())).thenReturn(List.of(new DiscoveredModel("gpt-test", null)));
        when(executor.executeConnectionTest(any(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
        GovernedModelTestExecutionPort port = new GovernedModelTestExecutionPort(connections, models,
                executor, registry, new ObjectMapper());

        ModelTestService.TestExecutionResult result = port.testConnection(target());

        assertThat(result.success()).isTrue();
        assertThat(result.discoveredModels()).containsExactly("gpt-test");
        ArgumentCaptor<com.dong.ddrag.modelplatform.runtime.ModelInvocationContext> context =
                ArgumentCaptor.forClass(com.dong.ddrag.modelplatform.runtime.ModelInvocationContext.class);
        verify(executor).executeConnectionTest(context.capture(), any());
        assertThat(context.getValue().scenario()).isEqualTo(ModelScenario.CONNECTION_TEST);
        assertThat(context.getValue().modelId()).isNull();
    }

    private static ModelTestService.TestTarget target() {
        return new ModelTestService.TestTarget(7L, 11L, 21L, "gpt-test", ConnectionOwnerType.USER, 1L);
    }

    private static ModelConnectionEntity connection() {
        ModelConnectionEntity connection = new ModelConnectionEntity();
        connection.setId(11L);
        connection.setOwnerType("USER");
        connection.setOwnerUserId(7L);
        connection.setProviderType("OPENAI");
        connection.setName("private");
        connection.setConfigVersion(1L);
        connection.setStatus("UNVERIFIED");
        connection.setBaseUrl("https://example.invalid");
        connection.setApiKeyPlaintext("test-key");
        return connection;
    }

    private static ModelConnectionModelEntity model() {
        ModelConnectionModelEntity model = new ModelConnectionModelEntity();
        model.setId(21L);
        model.setConnectionId(11L);
        model.setModelName("gpt-test");
        model.setEnabled(false);
        model.setTestStatus("UNVERIFIED");
        return model;
    }
}
