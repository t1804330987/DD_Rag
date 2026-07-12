package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.AssistantTurnRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssistantTurnRequestMapper {
    int insert(AssistantTurnRequestEntity entity);
    AssistantTurnRequestEntity selectByUserIdAndRequestId(@Param("userId") Long userId, @Param("requestId") String requestId);
    int bindPrepared(@Param("id") Long id, @Param("userId") Long userId, @Param("sessionId") Long sessionId,
                     @Param("userMessageId") Long userMessageId, @Param("updatedAt") java.time.LocalDateTime updatedAt);
    int completeByUserIdAndRequestId(@Param("userId") Long userId, @Param("requestId") String requestId,
                                     @Param("assistantMessageId") Long assistantMessageId,
                                     @Param("completedAt") java.time.LocalDateTime completedAt);
    int failByUserIdAndRequestId(@Param("userId") Long userId, @Param("requestId") String requestId,
                                 @Param("failureCode") String failureCode,
                                 @Param("completedAt") java.time.LocalDateTime completedAt);
}
