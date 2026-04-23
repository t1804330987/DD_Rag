package com.dong.ddrag.ingestion.mapper;

import com.dong.ddrag.ingestion.model.entity.IngestionJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IngestionJobMapper {

    int insert(IngestionJobEntity job);

    List<IngestionJobEntity> selectRunnableJobs(
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    IngestionJobEntity selectById(@Param("jobId") Long jobId);

    int claimRunning(
            @Param("jobId") Long jobId,
            @Param("pendingStatus") String pendingStatus,
            @Param("runningStatus") String runningStatus,
            @Param("workerId") String workerId,
            @Param("startedAt") LocalDateTime startedAt
    );

    int markSucceeded(
            @Param("jobId") Long jobId,
            @Param("runningStatus") String runningStatus,
            @Param("succeededStatus") String succeededStatus,
            @Param("workerId") String workerId,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    int markFailed(
            @Param("jobId") Long jobId,
            @Param("runningStatus") String runningStatus,
            @Param("failedStatus") String failedStatus,
            @Param("workerId") String workerId,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("lastError") String lastError
    );
}
