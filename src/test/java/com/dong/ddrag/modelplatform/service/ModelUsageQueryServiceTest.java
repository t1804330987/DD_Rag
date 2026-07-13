package com.dong.ddrag.modelplatform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.mapper.ModelCallLedgerMapper;
import com.dong.ddrag.modelplatform.mapper.ModelCallLedgerMapper.UsageAggregateRow;
import com.dong.ddrag.modelplatform.model.entity.ModelCallLedgerEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelUsageQueryServiceTest {
    @Test
    void returnsIndividualUsageRecordsWithCallTimeAndModel() {
        ModelCallLedgerMapper mapper = mock(ModelCallLedgerMapper.class);
        UsageAggregateRow aggregate = new UsageAggregateRow();
        aggregate.setProviderType("OPENAI");
        aggregate.setModelName("gpt-test");
        aggregate.setScenario("ASSISTANT_CHAT");
        aggregate.setLogicalStatus("SUCCEEDED");
        aggregate.setTransportStatus("TERMINATED");
        aggregate.setInvocationCount(1);
        aggregate.setInputTokens(12);
        aggregate.setOutputTokens(7);
        aggregate.setTotalTokens(19);
        aggregate.setDurationMs(250);
        when(mapper.aggregateUsage(eq(7L), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(aggregate));

        ModelCallLedgerEntity ledger = new ModelCallLedgerEntity();
        ledger.setStartedAt(LocalDateTime.of(2026, 7, 13, 20, 30));
        ledger.setProviderTypeSnapshot("OPENAI");
        ledger.setModelNameSnapshot("gpt-test");
        ledger.setTotalTokens(19L);
        ledger.setDurationMs(250L);
        ledger.setScenario("ASSISTANT_CHAT");
        ledger.setLogicalStatus("SUCCEEDED");
        ledger.setTransportStatus("TERMINATED");
        when(mapper.selectUsageRecords(eq(7L), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(ledger));

        ModelUsageQueryService service = new ModelUsageQueryService(mapper,
                Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC));

        ModelUsageQueryService.UsageReport report = service.queryUserUsage(7L, null);

        assertThat(report.records()).singleElement().satisfies(record -> {
            assertThat(record.startedAt()).isEqualTo(LocalDateTime.of(2026, 7, 13, 20, 30));
            assertThat(record.modelName()).isEqualTo("gpt-test");
            assertThat(record.totalTokens()).isEqualTo(19L);
            assertThat(record.durationMs()).isEqualTo(250L);
        });
    }
}
