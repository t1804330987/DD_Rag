package com.dong.ddrag.modelplatform.runtime;

import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import com.dong.ddrag.modelplatform.provider.ProviderConnectionSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class ChatModelFactory {
    private static final int DEFAULT_MAXIMUM_SIZE = 128;
    private static final Duration DEFAULT_IDLE_EXPIRY = Duration.ofMinutes(15);

    private final ChatModelProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maximumSize;
    private final Duration idleExpiry;
    private final Map<CacheKey, CacheEntry> entries = new HashMap<>();

    @Autowired
    public ChatModelFactory(ChatModelProviderRegistry providerRegistry, ObjectMapper objectMapper) {
        this(providerRegistry, objectMapper, Clock.systemUTC(), DEFAULT_MAXIMUM_SIZE, DEFAULT_IDLE_EXPIRY);
    }

    ChatModelFactory(ChatModelProviderRegistry providerRegistry, Clock clock, int maximumSize,
                     Duration idleExpiry) {
        this(providerRegistry, new ObjectMapper(), clock, maximumSize, idleExpiry);
    }

    private ChatModelFactory(ChatModelProviderRegistry providerRegistry, ObjectMapper objectMapper, Clock clock,
                             int maximumSize, Duration idleExpiry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumSize <= 0 || idleExpiry == null || idleExpiry.isNegative() || idleExpiry.isZero()) {
            throw new IllegalArgumentException("Cache size and idle expiry must be positive");
        }
        this.maximumSize = maximumSize;
        this.idleExpiry = idleExpiry;
    }

    synchronized ChatModelLease acquire(ModelConnectionEntity connection,
                                        ModelConnectionModelEntity model) {
        validate(connection, model);
        Instant now = clock.instant();
        retireExpired(now);
        CacheKey key = new CacheKey(connection.getId(), connection.getConfigVersion(), model.getModelName());
        CacheEntry entry = entries.get(key);
        if (entry == null || entry.retired) {
            boolean cacheable = ensureCapacity();
            ChatModel created = providerRegistry.require(parseProvider(connection.getProviderType()))
                    .createChatModel(snapshot(connection), model.getModelName());
            entry = new CacheEntry(created, now);
            if (cacheable) {
                entries.put(key, entry);
            } else {
                entry.retired = true;
            }
        }
        entry.references++;
        entry.lastAccess = now;
        return new ChatModelLease(this, entry);
    }

    public synchronized void invalidateConnection(Long connectionId) {
        entries.entrySet().removeIf(entry -> {
            if (!Objects.equals(entry.getKey().connectionId(), connectionId)) return false;
            retire(entry.getValue());
            return true;
        });
    }

    public synchronized void cleanUp() {
        retireExpired(clock.instant());
    }

    synchronized int size() {
        return entries.size();
    }

    @PreDestroy
    public synchronized void close() {
        entries.values().forEach(this::retire);
        entries.clear();
    }

    private void release(CacheEntry entry) {
        synchronized (this) {
            if (entry.references > 0) entry.references--;
            if (entry.retired && entry.references == 0) closeModel(entry);
        }
    }

    private void retireExpired(Instant now) {
        entries.entrySet().removeIf(item -> {
            CacheEntry entry = item.getValue();
            if (entry.references > 0 || entry.lastAccess.plus(idleExpiry).isAfter(now)) return false;
            retire(entry);
            return true;
        });
    }

    private boolean ensureCapacity() {
        while (entries.size() >= maximumSize) {
            Map.Entry<CacheKey, CacheEntry> oldest = entries.entrySet().stream()
                    .filter(item -> item.getValue().references == 0)
                    .min(Comparator.comparing(item -> item.getValue().lastAccess)).orElse(null);
            if (oldest == null) return false;
            entries.remove(oldest.getKey());
            retire(oldest.getValue());
        }
        return true;
    }

    private void retire(CacheEntry entry) {
        entry.retired = true;
        if (entry.references == 0) closeModel(entry);
    }

    private void closeModel(CacheEntry entry) {
        if (entry.closed) return;
        entry.closed = true;
        if (entry.model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Provider cleanup must not make cache invalidation fail.
            }
        }
    }

    private ProviderConnectionSnapshot snapshot(ModelConnectionEntity connection) {
        return new ProviderConnectionSnapshot(connection.getBaseUrl(), connection.readApiKeyPlaintext(),
                parseOptions(connection.getProviderOptionsJson()));
    }

    private Map<String, Object> parseOptions(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid provider options", exception);
        }
    }

    private static void validate(ModelConnectionEntity connection, ModelConnectionModelEntity model) {
        if (connection == null || model == null || connection.getId() == null
                || connection.getConfigVersion() == null || model.getModelName() == null
                || !Objects.equals(connection.getId(), model.getConnectionId())) {
            throw new IllegalArgumentException("Connection and model cache identity must match");
        }
    }

    private static ProviderType parseProvider(String value) {
        try {
            return ProviderType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Unsupported model provider", exception);
        }
    }

    private record CacheKey(Long connectionId, Long configVersion, String modelName) { }

    private static final class CacheEntry {
        private final ChatModel model;
        private Instant lastAccess;
        private int references;
        private boolean retired;
        private boolean closed;

        private CacheEntry(ChatModel model, Instant lastAccess) {
            this.model = Objects.requireNonNull(model, "model");
            this.lastAccess = lastAccess;
        }
    }

    static final class ChatModelLease implements AutoCloseable {
        private final ChatModelFactory owner;
        private CacheEntry entry;

        private ChatModelLease(ChatModelFactory owner, CacheEntry entry) {
            this.owner = owner;
            this.entry = entry;
        }

        synchronized ChatModel model() {
            CacheEntry current = entry;
            if (current == null) throw new IllegalStateException("Chat model lease is closed");
            return current.model;
        }

        @Override
        public synchronized void close() {
            CacheEntry current = entry;
            if (current == null) return;
            entry = null;
            owner.release(current);
        }
    }
}
