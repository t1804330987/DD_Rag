package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import com.dong.ddrag.modelplatform.provider.DiscoveredModel;
import com.dong.ddrag.modelplatform.provider.ProviderConnectionSnapshot;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/** The only production bridge from connection/model tests to a Provider. */
@Component
final class GovernedModelTestExecutionPort implements ModelTestService.ModelTestExecutionPort {
    private static final String CONNECTION_TEST_MODEL = "connection-test";
    private static final Prompt MODEL_TEST_PROMPT = new Prompt("Reply with exactly: OK");

    private final ModelConnectionMapper connectionMapper;
    private final ModelConnectionModelMapper modelMapper;
    private final ModelInvocationExecutor executor;
    private final ChatModelProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    GovernedModelTestExecutionPort(ModelConnectionMapper connectionMapper,
                                   ModelConnectionModelMapper modelMapper,
                                   ModelInvocationExecutor executor,
                                   ChatModelProviderRegistry providerRegistry,
                                   ObjectMapper objectMapper) {
        this.connectionMapper = connectionMapper;
        this.modelMapper = modelMapper;
        this.executor = executor;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelTestService.TestExecutionResult testConnection(ModelTestService.TestTarget target) {
        try {
            ModelConnectionEntity connection = requireOwnedConnection(target);
            ModelInvocationContext context = context(target, connection, null, ModelScenario.CONNECTION_TEST);
            List<String> discovered = executor.executeConnectionTest(context, () -> providerRegistry
                    .require(context.providerType()).discoverModels(snapshot(connection)).stream()
                    .map(DiscoveredModel::name).toList());
            return new ModelTestService.TestExecutionResult(true, null, discovered);
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    @Override
    public ModelTestService.TestExecutionResult testModel(ModelTestService.TestTarget target) {
        try {
            ModelConnectionEntity connection = requireOwnedConnection(target);
            ModelConnectionModelEntity model = modelMapper.selectById(target.modelId(), target.connectionId());
            if (model == null) throw new BusinessException("MODEL_NOT_FOUND");
            executor.call(context(target, connection, model, ModelScenario.MODEL_TEST), MODEL_TEST_PROMPT);
            return new ModelTestService.TestExecutionResult(true, null);
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    private ModelConnectionEntity requireOwnedConnection(ModelTestService.TestTarget target) {
        Long ownerUserId = target.ownerType().name().equals("USER") ? target.actorUserId() : null;
        ModelConnectionEntity connection = connectionMapper.selectOwnedById(target.connectionId(),
                target.ownerType().name(), ownerUserId);
        if (connection == null) throw new BusinessException("MODEL_CONNECTION_NOT_FOUND");
        return connection;
    }

    private ModelInvocationContext context(ModelTestService.TestTarget target, ModelConnectionEntity connection,
                                           ModelConnectionModelEntity model, ModelScenario scenario) {
        ProviderType providerType;
        try {
            providerType = ProviderType.valueOf(connection.getProviderType());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("MODEL_PROVIDER_UNSUPPORTED");
        }
        return new ModelInvocationContext(target.actorUserId(), scenario, target.connectionId(),
                model == null ? null : target.modelId(), target.configVersion(), providerType,
                model == null ? CONNECTION_TEST_MODEL : model.getModelName(), connection.getName(), target.ownerType(),
                null, null, null, null, null);
    }

    private ProviderConnectionSnapshot snapshot(ModelConnectionEntity connection) {
        try {
            Map<String, Object> options = connection.getProviderOptionsJson() == null
                    || connection.getProviderOptionsJson().isBlank() ? Map.of()
                    : objectMapper.readValue(connection.getProviderOptionsJson(), new TypeReference<>() { });
            return new ProviderConnectionSnapshot(connection.getBaseUrl(), connection.readApiKeyPlaintext(), options);
        } catch (Exception exception) {
            throw new BusinessException("MODEL_CONFIGURATION_INVALID");
        }
    }

    private static ModelTestService.TestExecutionResult failed(RuntimeException exception) {
        String code = exception instanceof BusinessException business ? business.getMessage() : "PROVIDER_ERROR";
        return new ModelTestService.TestExecutionResult(false, code);
    }
}
