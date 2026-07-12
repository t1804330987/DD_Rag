package com.dong.ddrag.modelplatform;

import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.modelplatform.mapper.ModelCallLedgerMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelCallLedgerEntity;
import com.dong.ddrag.modelplatform.model.enums.LedgerLogicalStatus;
import com.dong.ddrag.modelplatform.model.enums.LedgerTransportStatus;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.service.ModelCallLedgerService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType.PLATFORM;
import static com.dong.ddrag.modelplatform.model.enums.ModelScenario.ASSISTANT_CHAT;
import static com.dong.ddrag.modelplatform.model.enums.ProviderType.OPENAI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelCallLedgerServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-11T04:00:00Z");

    private ModelCallLedgerMapper mapper;
    private ModelCallLedgerService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ModelCallLedgerMapper.class);
        ModelRuntimeProperties properties = new ModelRuntimeProperties();
        service = new ModelCallLedgerService(mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldCreateRunningActiveLedgerBeforeInvocation() {
        when(mapper.insert(any())).thenReturn(1);

        ModelCallLedgerEntity ledger = service.start("inv-1", context("turn-1", "req-1"), null, null, null);

        assertThat(ledger.getInvocationId()).isEqualTo("inv-1");
        assertThat(ledger.getLogicalStatus()).isEqualTo("RUNNING");
        assertThat(ledger.getTransportStatus()).isEqualTo("ACTIVE");
        assertThat(ledger.getProviderTypeSnapshot()).isEqualTo("OPENAI");
        assertThat(ledger.getModelNameSnapshot()).isEqualTo("gpt-test");
        assertThat(ledger.getTurnId()).isEqualTo("turn-1");
        assertThat(ledger.getRequestId()).isEqualTo("req-1");
        assertThat(ledger.getStartedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(mapper).insert(ledger);
    }

    @Test
    void shouldUseCasSoDuplicateOrLateTerminalCallbacksCannotOverwriteCompletion() {
        when(mapper.completeIfRunning(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1, 0, 0);

        assertThat(service.succeed("inv-1", 10, 20, 30, 120, 91L)).isTrue();
        assertThat(service.fail("inv-1", "PROVIDER_ERROR", "safe summary", 150)).isFalse();
        assertThat(service.requestCancellation("inv-1", 160)).isFalse();

        verify(mapper).completeIfRunning("inv-1", "SUCCEEDED", "TERMINATED", 10L, 20L, 30L,
                120L, 91L, null, null);
    }

    @Test
    void shouldRepresentCancellationAndDetachedTransportSeparately() {
        when(mapper.requestCancellationIfRunning(eq("inv-1"), eq(80L), any())).thenReturn(1);
        when(mapper.terminateCancellation(eq("inv-1"), eq(90L), any())).thenReturn(1);

        assertThat(service.requestCancellation("inv-1", 80)).isTrue();
        assertThat(service.cancelled("inv-1", 90)).isTrue();

        verify(mapper).requestCancellationIfRunning("inv-1", 80L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(mapper).terminateCancellation("inv-1", 90L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldOnlyReconcileExpiredUnconfirmedActiveOrDetachedCalls() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(6);
        ModelCallLedgerEntity confirmed = ledger("confirmed", "RUNNING", "ACTIVE");
        ModelCallLedgerEntity orphanActive = ledger("orphan-active", "RUNNING", "ACTIVE");
        ModelCallLedgerEntity orphanDetached = ledger("orphan-detached", "CANCEL_REQUESTED", "DETACHED");
        when(mapper.selectStaleCandidates(cutoff)).thenReturn(List.of(confirmed, orphanActive, orphanDetached));
        when(mapper.hardTimeoutIfUnfinished(any(), any(), any(), any())).thenReturn(1);

        int reconciled = service.reconcileStale(Set.of("confirmed"));

        assertThat(reconciled).isEqualTo(2);
        verify(mapper, never()).hardTimeoutIfUnfinished("confirmed", 360_000L, "PROCESS_INTERRUPTED",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(mapper).hardTimeoutIfUnfinished("orphan-active", 360_000L, "PROCESS_INTERRUPTED",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(mapper).hardTimeoutIfUnfinished("orphan-detached", 360_000L, "PROCESS_INTERRUPTED",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldKeepSeparateLedgerRowsForInvocationsSharingTurnAndRequest() {
        when(mapper.insert(any())).thenReturn(1);

        ModelCallLedgerEntity first = service.start("inv-1", context("turn-shared", "req-shared"), null, null, null);
        ModelCallLedgerEntity second = service.start("inv-2", context("turn-shared", "req-shared"), null, null, null);

        assertThat(first.getInvocationId()).isNotEqualTo(second.getInvocationId());
        assertThat(first.getTurnId()).isEqualTo(second.getTurnId());
        assertThat(first.getRequestId()).isEqualTo(second.getRequestId());
    }

    @Test
    void shouldReturnExistingLedgerWhenInvocationStartIsReplayed() {
        ModelCallLedgerEntity existing = ledger("inv-1", "RUNNING", "ACTIVE");
        when(mapper.insert(any())).thenReturn(0);
        when(mapper.selectByInvocationId("inv-1")).thenReturn(existing);

        assertThat(service.start("inv-1", context("turn-1", "req-1"), null, null, null)).isSameAs(existing);
    }

    @Test
    void shouldPersistOnlySanitizedStableFailureDetails() {
        when(mapper.completeIfRunning(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.fail("inv-1", "provider_rate_limited",
                "Bearer secret-token apiKey=top-secret sk-secret-value\nremote failure", 12);

        verify(mapper).completeIfRunning("inv-1", "FAILED", "TERMINATED", null, null, null, 12L,
                null, "PROVIDER_RATE_LIMITED", "Bearer [REDACTED] apiKey=[REDACTED] [REDACTED] remote failure");
    }

    @Test
    void shouldNotPersistPromptOrModelResponseFromProviderError() {
        when(mapper.completeIfRunning(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.fail("inv-1", "PROVIDER_ERROR",
                "upstream rejected: {\"messages\":[{\"content\":\"user-private-prompt\"}]}", 12);

        verify(mapper).completeIfRunning("inv-1", "FAILED", "TERMINATED", null, null, null, 12L,
                null, "PROVIDER_ERROR", "Provider error details redacted");
    }

    @Test
    void shouldRedactGeminiAndQueryParameterApiKeysWithoutRemovingNormalErrorText() {
        when(mapper.completeIfRunning(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.fail("inv-1", "PROVIDER_ERROR",
                "Gemini request rejected: https://example.invalid?key=AIzaSyDUMMY_KEY_12345678901234567890", 12);

        verify(mapper).completeIfRunning("inv-1", "FAILED", "TERMINATED", null, null, null, 12L,
                null, "PROVIDER_ERROR",
                "Gemini request rejected: https://example.invalid?key=[REDACTED]");
    }

    private ModelInvocationContext context(String turnId, String requestId) {
        return new ModelInvocationContext(1001L, ASSISTANT_CHAT, 11L, 21L, 1L, OPENAI, "gpt-test",
                "platform-openai", PLATFORM, 31L, 41L, null, turnId, requestId);
    }

    private ModelCallLedgerEntity ledger(String invocationId, String logical, String transport) {
        ModelCallLedgerEntity entity = new ModelCallLedgerEntity();
        entity.setInvocationId(invocationId);
        entity.setLogicalStatus(LedgerLogicalStatus.valueOf(logical).name());
        entity.setTransportStatus(LedgerTransportStatus.valueOf(transport).name());
        return entity;
    }
}
