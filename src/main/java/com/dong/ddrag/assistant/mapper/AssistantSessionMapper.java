package com.dong.ddrag.assistant.mapper;

import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssistantSessionMapper {

    int insert(AssistantSessionEntity assistantSessionEntity);

    List<AssistantSessionEntity> selectByUserIdOrderByLastMessageAtDesc(@Param("userId") Long userId);

    AssistantSessionEntity selectByIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );

    int updateTitle(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("title") String title,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    int updateLastMessageAt(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("lastMessageAt") java.time.LocalDateTime lastMessageAt
    );

    int updateCurrentInstructionProfile(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("instructionProfileId") Long instructionProfileId,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    int updateCurrentModel(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("connectionId") Long connectionId,
            @Param("modelId") Long modelId,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    int clearCurrentInstructionProfile(
            @Param("userId") Long userId,
            @Param("instructionProfileId") Long instructionProfileId
    );

    int deleteByIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );
}
