package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.modelplatform.mapper.ModelCallLedgerMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelCallLedgerEntity;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelCallLedgerService {
    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;
    private static final String PROCESS_INTERRUPTED = "PROCESS_INTERRUPTED";
    private static final String REDACTED_ERROR_DETAILS = "Provider error details redacted";
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)((?:api[_-]?key|x-api-key|key|token|access[_-]?token)\\s*[=:]\\s*[\\\"']?)[^\\s,;\\\"'}&]+");
    private static final Pattern GEMINI_API_KEY = Pattern.compile("AIza[0-9A-Za-z_-]{20,}");
    private static final Pattern MODEL_CONTENT_FIELD = Pattern.compile(
            "(?is)(?:\\\"|')?(?:prompt|messages?|input|output|response|content)(?:\\\"|')?\\s*[:=]");

    private final ModelCallLedgerMapper mapper;
    private final ModelRuntimeProperties properties;
    private final Clock clock;

    @Autowired
    public ModelCallLedgerService(ModelCallLedgerMapper mapper, ModelRuntimeProperties properties) {
        this(mapper, properties, Clock.systemUTC());
    }

    public ModelCallLedgerService(ModelCallLedgerMapper mapper, ModelRuntimeProperties properties, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelCallLedgerEntity start(String invocationId, ModelInvocationContext context,
            Long instructionProfileId, Long instructionVersionId, Integer instructionVersionSnapshot) {
        requireText(invocationId, "invocationId");
        Objects.requireNonNull(context, "context");
        LocalDateTime now = now();
        ModelCallLedgerEntity ledger = new ModelCallLedgerEntity();
        ledger.setInvocationId(invocationId);
        ledger.setUserId(context.userId());
        ledger.setScenario(context.scenario().name());
        ledger.setSessionId(context.sessionId());
        ledger.setUserMessageId(context.userMessageId());
        ledger.setAssistantMessageId(context.assistantMessageId());
        ledger.setTurnId(context.turnId());
        ledger.setRequestId(context.requestId());
        ledger.setConnectionId(context.connectionId());
        ledger.setModelId(context.modelId());
        ledger.setProviderTypeSnapshot(context.providerType().name());
        ledger.setModelNameSnapshot(context.modelName());
        ledger.setConnectionNameSnapshot(context.connectionName());
        ledger.setOwnerTypeSnapshot(context.ownerType().name());
        ledger.setInstructionProfileId(instructionProfileId);
        ledger.setInstructionVersionId(instructionVersionId);
        ledger.setInstructionVersionSnapshot(instructionVersionSnapshot);
        ledger.setLogicalStatus("RUNNING");
        ledger.setTransportStatus("ACTIVE");
        ledger.setStartedAt(now);
        ledger.setCreatedAt(now);
        ledger.setUpdatedAt(now);
        if (mapper.insert(ledger) == 0) {
            return mapper.selectByInvocationId(invocationId);
        }
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean succeed(String invocationId, long inputTokens, long outputTokens, long totalTokens,
            long durationMs, Long assistantMessageId) {
        validateUsage(inputTokens, outputTokens, totalTokens, durationMs);
        return mapper.completeIfRunning(invocationId, "SUCCEEDED", "TERMINATED", inputTokens, outputTokens,
                totalTokens, durationMs, assistantMessageId, null, null) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(String invocationId, String errorCategory, String errorSummary, long durationMs) {
        requireText(errorCategory, "errorCategory");
        requireNonNegative(durationMs, "durationMs");
        return mapper.completeIfRunning(invocationId, "FAILED", "TERMINATED", null, null, null, durationMs,
                null, normalizeCategory(errorCategory), sanitizeSummary(errorSummary)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean timeout(String invocationId, String errorCategory, long durationMs) {
        requireText(errorCategory, "errorCategory");
        requireNonNegative(durationMs, "durationMs");
        return mapper.completeIfRunning(invocationId, "TIMEOUT", "TERMINATED", null, null, null, durationMs,
                null, normalizeCategory(errorCategory), null) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean requestCancellation(String invocationId, long durationMs) {
        requireNonNegative(durationMs, "durationMs");
        return mapper.requestCancellationIfRunning(invocationId, durationMs, now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean detachBusinessTimeout(String invocationId, String errorCategory, long durationMs) {
        requireText(errorCategory, "errorCategory");
        requireNonNegative(durationMs, "durationMs");
        return mapper.detachBusinessTimeoutIfRunning(invocationId, normalizeCategory(errorCategory), durationMs, now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean terminateBusinessTimeout(String invocationId, long durationMs) {
        requireNonNegative(durationMs, "durationMs");
        return mapper.terminateBusinessTimeout(invocationId, durationMs, now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelled(String invocationId, long durationMs) {
        requireNonNegative(durationMs, "durationMs");
        return mapper.terminateCancellation(invocationId, durationMs, now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean hardTimeout(String invocationId, long durationMs) {
        requireNonNegative(durationMs, "durationMs");
        return mapper.hardTimeoutIfUnfinished(invocationId, durationMs, PROCESS_INTERRUPTED, now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reconcileStale(Set<String> confirmedActiveInvocationIds) {
        Set<String> confirmed = confirmedActiveInvocationIds == null ? Set.of() : Set.copyOf(confirmedActiveInvocationIds);
        LocalDateTime now = now();
        LocalDateTime cutoff = now.minus(properties.getTimeout().getTransportHard());
        long durationMs = properties.getTimeout().getTransportHard().toMillis();
        return mapper.selectStaleCandidates(cutoff).stream()
                .filter(ledger -> !confirmed.contains(ledger.getInvocationId()))
                .mapToInt(ledger -> mapper.hardTimeoutIfUnfinished(
                        ledger.getInvocationId(), durationMs, PROCESS_INTERRUPTED, now))
                .sum();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private void validateUsage(long inputTokens, long outputTokens, long totalTokens, long durationMs) {
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(totalTokens, "totalTokens");
        requireNonNegative(durationMs, "durationMs");
        if (totalTokens < inputTokens + outputTokens) {
            throw new IllegalArgumentException("totalTokens must cover input and output tokens");
        }
    }

    private String normalizeCategory(String category) {
        String normalized = category.trim().toUpperCase();
        if (!normalized.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("errorCategory must be a stable internal code");
        }
        return normalized;
    }

    private String sanitizeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String sanitized = summary.replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer [REDACTED]")
                .replaceAll("(?i)(authorization\\s*[=:]\\s*)[^\\s,;]+", "$1[REDACTED]");
        sanitized = NAMED_SECRET.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = GEMINI_API_KEY.matcher(sanitized).replaceAll("[REDACTED]")
                .replaceAll("sk-[A-Za-z0-9_-]+", "[REDACTED]")
                .replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (MODEL_CONTENT_FIELD.matcher(sanitized).find()) {
            return REDACTED_ERROR_DETAILS;
        }
        return sanitized.length() <= MAX_ERROR_SUMMARY_LENGTH
                ? sanitized : sanitized.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
