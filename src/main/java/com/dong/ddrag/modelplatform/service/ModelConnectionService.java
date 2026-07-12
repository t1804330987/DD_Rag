package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ConnectionStatus;
import com.dong.ddrag.modelplatform.model.enums.ModelSourceType;
import com.dong.ddrag.modelplatform.model.enums.ModelTestStatus;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import com.dong.ddrag.modelplatform.provider.ProviderFieldSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConnectionService {
    private static final Map<ProviderType, List<String>> BUILT_IN_MODELS = Map.of(
            ProviderType.DASHSCOPE, List.of("qwen-plus", "qwen-max"),
            ProviderType.OPENAI, List.of("gpt-4o-mini", "gpt-4.1-mini"),
            ProviderType.GEMINI, List.of("gemini-2.5-flash", "gemini-2.5-pro"),
            ProviderType.ANTHROPIC, List.of("claude-sonnet-4-5", "claude-haiku-4-5"));

    private final ModelConnectionMapper connectionMapper;
    private final ModelConnectionModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final ChatModelProviderRegistry providerRegistry;

    public ModelConnectionService(ModelConnectionMapper connectionMapper, ModelConnectionModelMapper modelMapper,
                                  ObjectMapper objectMapper, ChatModelProviderRegistry providerRegistry) {
        this.connectionMapper = connectionMapper;
        this.modelMapper = modelMapper;
        this.objectMapper = objectMapper;
        this.providerRegistry = providerRegistry;
    }

    @Transactional
    public ConnectionView createUserConnection(Long userId, ConnectionCommand command) {
        return create("USER", requireId(userId), command);
    }

    @Transactional
    public ConnectionView createPlatformConnection(ConnectionCommand command) {
        return create("PLATFORM", null, command);
    }

    public List<ConnectionView> listUserConnections(Long userId) {
        return connectionMapper.selectAllByOwner("USER", requireId(userId)).stream().map(this::view).toList();
    }

    public List<ConnectionView> listPlatformConnections() {
        return connectionMapper.selectAllByOwner("PLATFORM", null).stream().map(this::view).toList();
    }

    public ConnectionView getUserConnection(Long userId, Long connectionId) {
        return view(requireOwned(connectionId, "USER", requireId(userId)));
    }

    public List<ModelView> listUserModels(Long userId, Long connectionId) {
        requireOwned(connectionId, "USER", requireId(userId));
        return modelMapper.selectByConnectionId(connectionId).stream().map(this::modelView).toList();
    }

    public List<ModelView> listPlatformModels(Long connectionId) {
        requireOwned(connectionId, "PLATFORM", null);
        return modelMapper.selectByConnectionId(connectionId).stream().map(this::modelView).toList();
    }

    public ConnectionView getPlatformConnection(Long connectionId) {
        return view(requireOwned(connectionId, "PLATFORM", null));
    }

    @Transactional
    public ConnectionView updateUserConnection(Long userId, Long connectionId, ConnectionCommand command) {
        return update(connectionId, "USER", requireId(userId), command);
    }

    @Transactional
    public ConnectionView updatePlatformConnection(Long connectionId, ConnectionCommand command) {
        return update(connectionId, "PLATFORM", null, command);
    }

    @Transactional
    public void deleteUserConnection(Long userId, Long connectionId) {
        delete(connectionId, "USER", requireId(userId));
    }

    @Transactional
    public void deletePlatformConnection(Long connectionId) {
        delete(connectionId, "PLATFORM", null);
    }

    @Transactional
    public ConnectionView changeUserStatus(Long userId, Long connectionId, String requestedStatus) {
        return changeStatus(connectionId, "USER", requireId(userId), requestedStatus);
    }

    @Transactional
    public ConnectionView changePlatformStatus(Long connectionId, String requestedStatus) {
        return changeStatus(connectionId, "PLATFORM", null, requestedStatus);
    }

    @Transactional
    public List<ModelView> mergeCatalog(Long connectionId, String ownerType, Long ownerUserId,
                                        List<String> discoveredModels, List<String> manualModels) {
        ModelConnectionEntity connection = requireOwned(connectionId, ownerType, ownerUserId);
        LinkedHashMap<String, ModelSourceType> merged = new LinkedHashMap<>();
        BUILT_IN_MODELS.getOrDefault(ProviderType.valueOf(connection.getProviderType()), List.of())
                .forEach(name -> merged.put(name, ModelSourceType.BUILT_IN));
        nullSafe(discoveredModels).forEach(name -> merged.put(normalizeModelName(name), ModelSourceType.DISCOVERED));
        nullSafe(manualModels).forEach(name -> merged.put(normalizeModelName(name), ModelSourceType.MANUAL));
        LocalDateTime now = LocalDateTime.now();
        merged.forEach((name, source) -> modelMapper.upsertCatalogModel(newModel(connectionId, name, source, now)));
        return modelMapper.selectByConnectionId(connectionId).stream().map(this::modelView).toList();
    }

    @Transactional
    public ModelView setUserModelEnabled(Long userId, Long connectionId, Long modelId, boolean enabled) {
        ModelConnectionEntity connection = requireOwned(connectionId, "USER", requireId(userId));
        return setModelEnabled(connection, modelId, enabled);
    }

    @Transactional
    public void hideUserModel(Long userId, Long connectionId, Long modelId) {
        ModelConnectionEntity connection = requireOwned(connectionId, "USER", requireId(userId));
        ModelConnectionModelEntity model = modelMapper.selectById(modelId, connection.getId());
        if (model == null) throw new BusinessException("MODEL_NOT_FOUND");
        if (modelMapper.hideById(modelId, connectionId, LocalDateTime.now()) != 1) {
            throw new BusinessException("MODEL_HIDE_FAILED");
        }
        model.setEnabled(false);
    }

    @Transactional
    public ModelView setPlatformModelEnabled(Long connectionId, Long modelId, boolean enabled) {
        ModelConnectionEntity connection = requireOwned(connectionId, "PLATFORM", null);
        return setModelEnabled(connection, modelId, enabled);
    }

    private ConnectionView create(String ownerType, Long ownerUserId, ConnectionCommand command) {
        validate(command);
        if (command.apiKey() == null || command.apiKey().isBlank()) {
            throw new BusinessException("MODEL_CONNECTION_API_KEY_REQUIRED");
        }
        LocalDateTime now = LocalDateTime.now();
        ModelConnectionEntity entity = new ModelConnectionEntity();
        applyCommand(entity, command);
        entity.setOwnerType(ownerType);
        entity.setOwnerUserId(ownerUserId);
        entity.setCredentialStorageType("ENCRYPTED");
        entity.setCredentialVersion(1);
        entity.setConfigVersion(1L);
        entity.setStatus(ConnectionStatus.UNVERIFIED.name());
        entity.setLastConnectionTestStatus(ModelTestStatus.UNVERIFIED.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (connectionMapper.insert(entity) != 1) throw new BusinessException("MODEL_CONNECTION_CREATE_FAILED");
        return view(entity);
    }

    private ConnectionView update(Long connectionId, String ownerType, Long ownerUserId, ConnectionCommand command) {
        validate(command);
        ModelConnectionEntity current = requireOwned(connectionId, ownerType, ownerUserId);
        long expectedVersion = current.getConfigVersion();
        String expectedStatus = current.getStatus();
        boolean updateApiKey = command.apiKey() != null && !command.apiKey().isBlank();
        boolean apiKeyChanged = updateApiKey && !Objects.equals(current.readApiKeyPlaintext(), command.apiKey());
        boolean criticalChanged = !Objects.equals(current.getProviderType(), command.providerType())
                || !Objects.equals(current.getBaseUrl(), command.baseUrl())
                || apiKeyChanged
                || !Objects.equals(readOptions(current), command.options());
        current.setName(command.name().trim());
        current.setMaxConcurrency(command.maxConcurrency());
        if (criticalChanged) {
            applyCommand(current, command);
            current.setConfigVersion(expectedVersion + 1);
            if (!ConnectionStatus.DISABLED.name().equals(expectedStatus)) {
                current.setStatus(ConnectionStatus.UNVERIFIED.name());
            }
            current.setLastConnectionTestStatus(ModelTestStatus.UNVERIFIED.name());
            current.setLastConnectionTestAt(null);
        }
        // Only rewrite api_key when the form actually submitted a new key.
        // Leaving the field blank means "keep existing secret" and must not re-persist the decrypted value.
        if (updateApiKey) {
            current.setCredentialStorageType("ENCRYPTED");
        }
        current.setUpdatedAt(LocalDateTime.now());
        if (connectionMapper.updateOwnedConfig(current, expectedVersion, expectedStatus, updateApiKey) != 1) {
            throw new BusinessException("MODEL_CONNECTION_CONCURRENTLY_MODIFIED");
        }
        if (criticalChanged) modelMapper.invalidateTests(connectionId, current.getUpdatedAt());
        return view(current);
    }

    private void delete(Long connectionId, String ownerType, Long ownerUserId) {
        requireOwned(connectionId, ownerType, ownerUserId);
        if (connectionMapper.softDeleteOwned(connectionId, ownerType, ownerUserId, LocalDateTime.now()) != 1) {
            throw new BusinessException("MODEL_CONNECTION_DELETE_FAILED");
        }
    }

    private ConnectionView changeStatus(Long connectionId, String ownerType, Long ownerUserId, String requestedStatus) {
        ModelConnectionEntity current = requireOwned(connectionId, ownerType, ownerUserId);
        ConnectionStatus target = parseStatus(requestedStatus);
        ConnectionStatus source = ConnectionStatus.valueOf(current.getStatus());
        if (target == ConnectionStatus.DELETED) {
            throw new BusinessException("MODEL_CONNECTION_DELETE_REQUIRES_DELETE_OPERATION");
        }
        if (!isAllowed(source, target)) throw new BusinessException("MODEL_CONNECTION_ILLEGAL_STATE_TRANSITION");
        if (connectionMapper.updateOwnedStatus(connectionId, ownerType, ownerUserId, source.name(),
                current.getConfigVersion(), target.name(),
                LocalDateTime.now()) != 1) throw new BusinessException("MODEL_CONNECTION_CONCURRENTLY_MODIFIED");
        if (source == ConnectionStatus.DISABLED && target == ConnectionStatus.UNVERIFIED) {
            modelMapper.invalidateTests(connectionId, LocalDateTime.now());
        }
        current.setStatus(target.name());
        return view(current);
    }

    private ModelView setModelEnabled(ModelConnectionEntity connection, Long modelId, boolean enabled) {
        ModelConnectionModelEntity model = modelMapper.selectById(modelId, connection.getId());
        if (model == null) throw new BusinessException("MODEL_NOT_FOUND");
        if (enabled && ConnectionStatus.DISABLED.name().equals(connection.getStatus())) {
            throw new BusinessException("MODEL_CONNECTION_DISABLED");
        }
        if (enabled && (!ModelTestStatus.PASSED.name().equals(model.getTestStatus())
                || !Objects.equals(model.getTestedConfigVersion(), connection.getConfigVersion()))) {
            throw new BusinessException("MODEL_NOT_TESTED");
        }
        if (modelMapper.updateEnabled(modelId, connection.getId(), connection.getConfigVersion(), enabled,
                LocalDateTime.now()) != 1) {
            throw new BusinessException("MODEL_CONNECTION_CONCURRENTLY_MODIFIED");
        }
        if (enabled && !ConnectionStatus.ACTIVE.name().equals(connection.getStatus())) {
            if (connectionMapper.updateOwnedStatus(connection.getId(), connection.getOwnerType(),
                    connection.getOwnerUserId(), connection.getStatus(), connection.getConfigVersion(),
                    ConnectionStatus.ACTIVE.name(),
                    LocalDateTime.now()) != 1) {
                throw new BusinessException("MODEL_CONNECTION_CONCURRENTLY_MODIFIED");
            }
            connection.setStatus(ConnectionStatus.ACTIVE.name());
        }
        model.setEnabled(enabled);
        return modelView(model);
    }

    private ModelConnectionEntity requireOwned(Long connectionId, String ownerType, Long ownerUserId) {
        if (connectionId == null || connectionId <= 0) throw new BusinessException("MODEL_CONNECTION_ID_INVALID");
        ModelConnectionEntity entity = connectionMapper.selectOwnedById(connectionId, ownerType, ownerUserId);
        if (entity == null) throw new BusinessException("MODEL_CONNECTION_NOT_FOUND");
        return entity;
    }

    private void applyCommand(ModelConnectionEntity entity, ConnectionCommand command) {
        entity.setProviderType(parseProvider(command.providerType()).name());
        entity.setName(command.name().trim());
        entity.setBaseUrl(blankToNull(command.baseUrl()));
        if (command.apiKey() != null && !command.apiKey().isBlank()) {
            entity.setApiKeyPlaintext(command.apiKey());
            entity.setMaskedKeySuffix(suffix(command.apiKey()));
            entity.setCredentialStorageType("ENCRYPTED");
            if (entity.getCredentialVersion() != null) entity.setCredentialVersion(entity.getCredentialVersion() + 1);
        }
        entity.setProviderOptionsJson(writeOptions(command.options()));
        entity.setMaxConcurrency(command.maxConcurrency());
    }

    private void validate(ConnectionCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw new BusinessException("MODEL_CONNECTION_NAME_REQUIRED");
        }
        ProviderType providerType = parseProvider(command.providerType());
        providerRegistry.require(providerType).validateConnectionOptions(command.options());
        if (providerRegistry.require(providerType).connectionSchema().fields().stream()
                .anyMatch(field -> field.sensitive() && command.options() != null
                        && command.options().containsKey(field.name()))) {
            throw new BusinessException("MODEL_CONNECTION_SENSITIVE_OPTION_UNSUPPORTED");
        }
        if (command.maxConcurrency() != null && command.maxConcurrency() <= 0) {
            throw new BusinessException("MODEL_CONNECTION_CONCURRENCY_INVALID");
        }
    }

    private ConnectionView view(ModelConnectionEntity entity) {
        return new ConnectionView(entity.getId(), entity.getProviderType(), entity.getOwnerType(), entity.getName(),
                entity.getBaseUrl(), mask(entity.getMaskedKeySuffix()), presentationOptions(entity), entity.getMaxConcurrency(),
                entity.getStatus(), entity.getConfigVersion(), entity.getLastConnectionTestStatus(),
                entity.getLastConnectionTestAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ModelView modelView(ModelConnectionModelEntity entity) {
        return new ModelView(entity.getId(), entity.getConnectionId(), entity.getModelName(), entity.getSourceType(),
                entity.getTestStatus(), entity.getTestedConfigVersion(), entity.getLastTestAt(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getUpdatedAt());
    }

    private ModelConnectionModelEntity newModel(Long connectionId, String name, ModelSourceType source, LocalDateTime now) {
        ModelConnectionModelEntity entity = new ModelConnectionModelEntity();
        entity.setConnectionId(connectionId);
        entity.setModelName(name);
        entity.setSourceType(source.name());
        entity.setTestStatus(ModelTestStatus.UNVERIFIED.name());
        entity.setEnabled(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readOptions(ModelConnectionEntity entity) {
        if (entity.getProviderOptionsJson() == null || entity.getProviderOptionsJson().isBlank()) return Map.of();
        try { return objectMapper.readValue(entity.getProviderOptionsJson(), Map.class); }
        catch (JsonProcessingException e) { throw new BusinessException("MODEL_CONNECTION_OPTIONS_INVALID", e); }
    }

    private Map<String, Object> presentationOptions(ModelConnectionEntity entity) {
        Map<String, Object> stored = readOptions(entity);
        ProviderType providerType = parseProvider(entity.getProviderType());
        return stored.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> providerRegistry.require(providerType).connectionSchema().fields().stream()
                        .anyMatch(field -> field.name().equals(entry.getKey()) && !field.sensitive()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String writeOptions(Map<String, Object> options) {
        try { return objectMapper.writeValueAsString(options == null ? Map.of() : options); }
        catch (JsonProcessingException e) { throw new BusinessException("MODEL_CONNECTION_OPTIONS_INVALID", e); }
    }

    private static boolean isAllowed(ConnectionStatus source, ConnectionStatus target) {
        if (target == ConnectionStatus.DISABLED) return source != ConnectionStatus.DELETED && source != ConnectionStatus.DISABLED;
        if (source == ConnectionStatus.DISABLED && target == ConnectionStatus.UNVERIFIED) return true;
        return false;
    }

    private static ProviderType parseProvider(String value) {
        try { return ProviderType.valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BusinessException("MODEL_PROVIDER_INVALID"); }
    }

    private static ConnectionStatus parseStatus(String value) {
        try { return ConnectionStatus.valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BusinessException("MODEL_CONNECTION_STATUS_INVALID"); }
    }

    private static Long requireId(Long id) {
        if (id == null || id <= 0) throw new BusinessException("USER_ID_INVALID");
        return id;
    }

    private static String normalizeModelName(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("MODEL_NAME_REQUIRED");
        return value.trim();
    }

    private static List<String> nullSafe(List<String> values) { return values == null ? List.of() : new ArrayList<>(values); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String suffix(String key) { return key.substring(Math.max(0, key.length() - 4)); }
    private static String mask(String suffix) { return suffix == null || suffix.isBlank() ? null : "****" + suffix; }

    public record ConnectionCommand(String providerType, String name, String baseUrl, String apiKey,
                                    Map<String, Object> options, Integer maxConcurrency) { }
    public record ConnectionView(Long id, String providerType, String ownerType, String name, String baseUrl,
                                 String maskedApiKey, Map<String, Object> options, Integer maxConcurrency,
                                 String status, Long configVersion, String connectionTestStatus,
                                 LocalDateTime connectionTestAt, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record ModelView(Long id, Long connectionId, String modelName, String sourceType, String testStatus,
                            Long testedConfigVersion, LocalDateTime lastTestAt, boolean enabled,
                            LocalDateTime syncedAt) { }
}
