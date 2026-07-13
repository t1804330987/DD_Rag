package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.modelplatform.mapper.ModelCallLedgerMapper;
import com.dong.ddrag.modelplatform.mapper.ModelCallLedgerMapper.UsageAggregateRow;
import com.dong.ddrag.modelplatform.model.entity.ModelCallLedgerEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelUsageQueryService {
    private final ModelCallLedgerMapper mapper;
    private final Clock clock;

    @Autowired
    public ModelUsageQueryService(ModelCallLedgerMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    ModelUsageQueryService(ModelCallLedgerMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsageReport queryUserUsage(Long currentUserId, UsageFilter filter) {
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId is required");
        }
        return query(currentUserId, normalize(filter));
    }

    @Transactional(readOnly = true)
    public UsageReport queryAdminUsage(UsageFilter filter) {
        UsageFilter normalized = normalize(filter);
        return query(normalized.userId(), normalized);
    }

    private UsageReport query(Long userId, UsageFilter filter) {
        List<UsageGroup> groups = mapper.aggregateUsage(userId, filter.providerType(), filter.modelName(),
                        filter.scenario(), filter.logicalStatus(), filter.transportStatus(),
                        filter.startedAt(), filter.endedAt()).stream()
                .map(this::toGroup)
                .toList();
        List<UsageRecord> records = mapper.selectUsageRecords(userId, filter.providerType(), filter.modelName(),
                        filter.scenario(), filter.logicalStatus(), filter.transportStatus(),
                        filter.startedAt(), filter.endedAt()).stream()
                .map(this::toRecord)
                .toList();
        return new UsageReport(userId,
                groups.stream().mapToLong(UsageGroup::invocationCount).sum(),
                groups.stream().mapToLong(UsageGroup::inputTokens).sum(),
                groups.stream().mapToLong(UsageGroup::outputTokens).sum(),
                groups.stream().mapToLong(UsageGroup::totalTokens).sum(),
                groups.stream().mapToLong(UsageGroup::durationMs).sum(), groups, records);
    }

    private UsageFilter normalize(UsageFilter filter) {
        LocalDateTime now = LocalDateTime.now(clock);
        UsageFilter source = filter == null ? new UsageFilter(null, null, null, null, null, null, null, null) : filter;
        LocalDateTime startedAt = source.startedAt() == null ? now.minusDays(30) : source.startedAt();
        LocalDateTime endedAt = source.endedAt() == null ? now : source.endedAt();
        if (!startedAt.isBefore(endedAt)) {
            throw new IllegalArgumentException("startedAt must be before endedAt");
        }
        return new UsageFilter(source.userId(), normalize(source.providerType()), normalize(source.modelName()),
                normalize(source.scenario()), normalize(source.logicalStatus()), normalize(source.transportStatus()),
                startedAt, endedAt);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UsageGroup toGroup(UsageAggregateRow row) {
        return new UsageGroup(row.getProviderType(), row.getModelName(), row.getScenario(), row.getLogicalStatus(),
                row.getTransportStatus(), row.getInvocationCount(), row.getInputTokens(), row.getOutputTokens(),
                row.getTotalTokens(), row.getDurationMs());
    }

    private UsageRecord toRecord(ModelCallLedgerEntity ledger) {
        return new UsageRecord(ledger.getStartedAt(), ledger.getProviderTypeSnapshot(),
                ledger.getModelNameSnapshot(), ledger.getTotalTokens(), ledger.getDurationMs(),
                ledger.getScenario(), ledger.getLogicalStatus(), ledger.getTransportStatus());
    }

    public record UsageFilter(Long userId, String providerType, String modelName, String scenario,
            String logicalStatus, String transportStatus, LocalDateTime startedAt, LocalDateTime endedAt) { }

    public record UsageReport(Long userId, long invocationCount, long inputTokens, long outputTokens,
            long totalTokens, long durationMs, List<UsageGroup> groups, List<UsageRecord> records) { }

    public record UsageGroup(String providerType, String modelName, String scenario, String logicalStatus,
            String transportStatus, long invocationCount, long inputTokens, long outputTokens,
            long totalTokens, long durationMs) { }

    public record UsageRecord(LocalDateTime startedAt, String providerType, String modelName,
            Long totalTokens, Long durationMs, String scenario, String logicalStatus, String transportStatus) { }
}
