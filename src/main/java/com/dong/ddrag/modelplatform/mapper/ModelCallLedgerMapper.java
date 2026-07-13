package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.ModelCallLedgerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ModelCallLedgerMapper {
    int insert(ModelCallLedgerEntity entity);
    ModelCallLedgerEntity selectByInvocationId(@Param("invocationId") String invocationId);
    int completeIfRunning(@Param("invocationId") String invocationId,
            @Param("logicalStatus") String logicalStatus, @Param("transportStatus") String transportStatus,
            @Param("inputTokens") Long inputTokens, @Param("outputTokens") Long outputTokens,
            @Param("totalTokens") Long totalTokens, @Param("durationMs") Long durationMs,
            @Param("assistantMessageId") Long assistantMessageId, @Param("errorCategory") String errorCategory,
            @Param("errorSummary") String errorSummary);
    int requestCancellationIfRunning(@Param("invocationId") String invocationId,
            @Param("durationMs") Long durationMs, @Param("updatedAt") LocalDateTime updatedAt);
    int detachBusinessTimeoutIfRunning(@Param("invocationId") String invocationId,
            @Param("errorCategory") String errorCategory, @Param("durationMs") Long durationMs,
            @Param("updatedAt") LocalDateTime updatedAt);
    int terminateBusinessTimeout(@Param("invocationId") String invocationId,
            @Param("durationMs") Long durationMs, @Param("finishedAt") LocalDateTime finishedAt);
    int terminateCancellation(@Param("invocationId") String invocationId,
            @Param("durationMs") Long durationMs, @Param("finishedAt") LocalDateTime finishedAt);
    List<ModelCallLedgerEntity> selectStaleCandidates(@Param("cutoff") LocalDateTime cutoff);
    int hardTimeoutIfUnfinished(@Param("invocationId") String invocationId,
            @Param("durationMs") Long durationMs, @Param("errorCategory") String errorCategory,
            @Param("finishedAt") LocalDateTime finishedAt);
    List<ModelCallLedgerEntity> selectByUserAndStartedAtBetween(@Param("userId") Long userId,
            @Param("startedAt") LocalDateTime startedAt, @Param("endedAt") LocalDateTime endedAt);
    List<ModelCallLedgerEntity> selectForAdminUsage(@Param("userId") Long userId,
            @Param("providerType") String providerType, @Param("modelName") String modelName,
            @Param("scenario") String scenario, @Param("logicalStatus") String logicalStatus,
            @Param("startedAt") LocalDateTime startedAt, @Param("endedAt") LocalDateTime endedAt);
    List<UsageAggregateRow> aggregateUsage(@Param("userId") Long userId,
            @Param("providerType") String providerType, @Param("modelName") String modelName,
            @Param("scenario") String scenario, @Param("logicalStatus") String logicalStatus,
            @Param("transportStatus") String transportStatus,
            @Param("startedAt") LocalDateTime startedAt, @Param("endedAt") LocalDateTime endedAt);
    List<ModelCallLedgerEntity> selectUsageRecords(@Param("userId") Long userId,
            @Param("providerType") String providerType, @Param("modelName") String modelName,
            @Param("scenario") String scenario, @Param("logicalStatus") String logicalStatus,
            @Param("transportStatus") String transportStatus,
            @Param("startedAt") LocalDateTime startedAt, @Param("endedAt") LocalDateTime endedAt);

    class UsageAggregateRow {
        private String providerType;
        private String modelName;
        private String scenario;
        private String logicalStatus;
        private String transportStatus;
        private long invocationCount;
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private long durationMs;

        public String getProviderType() { return providerType; }
        public void setProviderType(String providerType) { this.providerType = providerType; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public String getScenario() { return scenario; }
        public void setScenario(String scenario) { this.scenario = scenario; }
        public String getLogicalStatus() { return logicalStatus; }
        public void setLogicalStatus(String logicalStatus) { this.logicalStatus = logicalStatus; }
        public String getTransportStatus() { return transportStatus; }
        public void setTransportStatus(String transportStatus) { this.transportStatus = transportStatus; }
        public long getInvocationCount() { return invocationCount; }
        public void setInvocationCount(long invocationCount) { this.invocationCount = invocationCount; }
        public long getInputTokens() { return inputTokens; }
        public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
        public long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }
}
