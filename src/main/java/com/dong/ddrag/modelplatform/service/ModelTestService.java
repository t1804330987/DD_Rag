package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ModelSourceType;
import com.dong.ddrag.modelplatform.model.enums.ModelTestStatus;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ModelTestService {
    private final ModelConnectionMapper connectionMapper;
    private final ModelConnectionModelMapper modelMapper;
    private final ModelTestExecutionPort executionPort;

    @Autowired
    public ModelTestService(ModelConnectionMapper connectionMapper, ModelConnectionModelMapper modelMapper,
                            ObjectProvider<ModelTestExecutionPort> executionPortProvider) {
        this(connectionMapper, modelMapper, executionPortProvider.getIfAvailable(UnwiredExecutionPort::new));
    }

    public ModelTestService(ModelConnectionMapper connectionMapper, ModelConnectionModelMapper modelMapper,
                            ModelTestExecutionPort executionPort) {
        this.connectionMapper = connectionMapper;
        this.modelMapper = modelMapper;
        this.executionPort = executionPort;
    }

    public TestOutcome testUserConnection(Long userId, Long connectionId) {
        return testConnection(userId, requireOwned(connectionId, "USER", userId));
    }

    public TestOutcome testPlatformConnection(Long actorUserId, Long connectionId) {
        return testConnection(actorUserId, requireOwned(connectionId, "PLATFORM", null));
    }

    public CatalogRefreshOutcome refreshUserModels(Long userId, Long connectionId) {
        return refreshModels(userId, requireOwned(connectionId, "USER", userId));
    }

    public CatalogRefreshOutcome refreshPlatformModels(Long actorUserId, Long connectionId) {
        return refreshModels(actorUserId, requireOwned(connectionId, "PLATFORM", null));
    }

    public TestOutcome testUserModel(Long userId, Long connectionId, Long modelId) {
        return testModel(userId, requireOwned(connectionId, "USER", userId), modelId);
    }

    public TestOutcome testPlatformModel(Long actorUserId, Long connectionId, Long modelId) {
        return testModel(actorUserId, requireOwned(connectionId, "PLATFORM", null), modelId);
    }

    public List<TestOutcome> testUserModelsSequentially(Long userId, Long connectionId, List<Long> modelIds) {
        ModelConnectionEntity connection = requireOwned(connectionId, "USER", userId);
        return testModelsSequentially(userId, connection, modelIds);
    }

    public List<TestOutcome> testPlatformModelsSequentially(Long actorUserId, Long connectionId, List<Long> modelIds) {
        ModelConnectionEntity connection = requireOwned(connectionId, "PLATFORM", null);
        return testModelsSequentially(actorUserId, connection, modelIds);
    }

    protected TestOutcome testConnection(Long actorUserId, ModelConnectionEntity connection) {
        long version = connection.getConfigVersion();
        TestExecutionResult result = executionPort.testConnection(
                TestTarget.connection(actorUserId, connection.getId(), parseOwnerType(connection), version));
        LocalDateTime testedAt = LocalDateTime.now();
        String testStatus = result.success() ? ModelTestStatus.PASSED.name() : ModelTestStatus.FAILED.name();
        boolean hasUsableModel = modelMapper.selectByConnectionId(connection.getId()).stream()
                .anyMatch(model -> Boolean.TRUE.equals(model.getEnabled())
                        && ModelTestStatus.PASSED.name().equals(model.getTestStatus())
                        && java.util.Objects.equals(model.getTestedConfigVersion(), version));
        String connectionStatus = connectionStatusAfterTest(connection.getStatus(), result.success(), hasUsableModel);
        int updated = connectionMapper.completeConnectionTestCas(connection.getId(), version, testStatus,
                connectionStatus, testedAt);
        if (updated == 1 && result.success()) {
            mergeDiscoveredModels(connection.getId(), result.discoveredModels(), testedAt);
        }
        return new TestOutcome(connection.getId(), null, version, testStatus, updated == 1, result.errorCode());
    }

    private CatalogRefreshOutcome refreshModels(Long actorUserId, ModelConnectionEntity connection) {
        TestExecutionResult result = executionPort.testConnection(TestTarget.connection(actorUserId,
                connection.getId(), parseOwnerType(connection), connection.getConfigVersion()));
        List<String> discoveredModels = normalizeDiscoveredModels(result.discoveredModels());
        if (result.success()) {
            mergeDiscoveredModels(connection.getId(), discoveredModels, LocalDateTime.now());
        }
        return new CatalogRefreshOutcome(connection.getId(), connection.getConfigVersion(), result.success(),
                result.errorCode(), result.success() ? discoveredModels.size() : 0);
    }

    private void mergeDiscoveredModels(Long connectionId, List<String> modelNames, LocalDateTime syncedAt) {
        for (String modelName : normalizeDiscoveredModels(modelNames)) {
            ModelConnectionModelEntity discovered = new ModelConnectionModelEntity();
            discovered.setConnectionId(connectionId);
            discovered.setModelName(modelName);
            discovered.setSourceType(ModelSourceType.DISCOVERED.name());
            discovered.setTestStatus(ModelTestStatus.UNVERIFIED.name());
            discovered.setEnabled(false);
            discovered.setCreatedAt(syncedAt);
            discovered.setUpdatedAt(syncedAt);
            modelMapper.upsertCatalogModel(discovered);
        }
    }

    private static String connectionStatusAfterTest(String currentStatus, boolean success, boolean hasUsableModel) {
        if (hasUsableModel) return "ACTIVE";
        if (success) return "FAILED".equals(currentStatus) ? "UNVERIFIED" : currentStatus;
        if ("ACTIVE".equals(currentStatus)) return "ACTIVE";
        return "FAILED";
    }

    protected TestOutcome testModel(Long actorUserId, ModelConnectionEntity connection, Long modelId) {
        ModelConnectionModelEntity model = modelMapper.selectById(modelId, connection.getId());
        if (model == null) throw new BusinessException("MODEL_NOT_FOUND");
        long version = connection.getConfigVersion();
        TestExecutionResult result = executionPort.testModel(
                TestTarget.model(actorUserId, connection.getId(), modelId, model.getModelName(),
                        parseOwnerType(connection), version));
        String status = result.success() ? ModelTestStatus.PASSED.name() : ModelTestStatus.FAILED.name();
        int updated = modelMapper.completeTestCas(modelId, connection.getId(), version, status, LocalDateTime.now());
        return new TestOutcome(connection.getId(), modelId, version, status, updated == 1, result.errorCode());
    }

    private List<TestOutcome> testModelsSequentially(Long actorUserId, ModelConnectionEntity connection,
                                                      List<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) throw new BusinessException("MODEL_IDS_REQUIRED");
        List<TestOutcome> outcomes = new ArrayList<>(modelIds.size());
        for (Long modelId : modelIds) outcomes.add(testModel(actorUserId, connection, modelId));
        return List.copyOf(outcomes);
    }

    private ModelConnectionEntity requireOwned(Long connectionId, String ownerType, Long ownerUserId) {
        ModelConnectionEntity connection = connectionMapper.selectOwnedById(connectionId, ownerType, ownerUserId);
        if (connection == null) throw new BusinessException("MODEL_CONNECTION_NOT_FOUND");
        return connection;
    }

    private static List<String> normalizeDiscoveredModels(List<String> modelNames) {
        return modelNames.stream()
                .filter(modelName -> modelName != null && !modelName.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static ConnectionOwnerType parseOwnerType(ModelConnectionEntity connection) {
        try {
            return ConnectionOwnerType.valueOf(connection.getOwnerType());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("MODEL_CONNECTION_OWNER_INVALID");
        }
    }

    public interface ModelTestExecutionPort {
        TestExecutionResult testConnection(TestTarget target);
        TestExecutionResult testModel(TestTarget target);
    }

    private static final class UnwiredExecutionPort implements ModelTestExecutionPort {
        @Override public TestExecutionResult testConnection(TestTarget target) {
            throw new BusinessException("MODEL_TEST_EXECUTOR_NOT_READY");
        }
        @Override public TestExecutionResult testModel(TestTarget target) {
            throw new BusinessException("MODEL_TEST_EXECUTOR_NOT_READY");
        }
    }

    public record TestTarget(Long actorUserId, Long connectionId, Long modelId, String modelName,
                             ConnectionOwnerType ownerType, long configVersion) {
        static TestTarget connection(Long actorUserId, Long connectionId, ConnectionOwnerType ownerType,
                                     long configVersion) {
            return new TestTarget(actorUserId, connectionId, null, null, ownerType, configVersion);
        }
        static TestTarget model(Long actorUserId, Long connectionId, Long modelId, String modelName,
                                ConnectionOwnerType ownerType, long configVersion) {
            return new TestTarget(actorUserId, connectionId, modelId, modelName, ownerType, configVersion);
        }
    }
    public record TestExecutionResult(boolean success, String errorCode, List<String> discoveredModels) {
        public TestExecutionResult(boolean success, String errorCode) {
            this(success, errorCode, List.of());
        }
        public TestExecutionResult {
            discoveredModels = discoveredModels == null ? List.of() : List.copyOf(discoveredModels);
        }
    }
    public record TestOutcome(Long connectionId, Long modelId, long configVersion, String status,
                              boolean applied, String errorCode) { }
    public record CatalogRefreshOutcome(Long connectionId, long configVersion, boolean success,
                                        String errorCode, int discoveredCount) { }
}
