package com.dong.ddrag.assistant.mapper;

import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssistantMessageMapper {

    int insert(AssistantMessageEntity assistantMessageEntity);

    Long countBySessionId(@Param("sessionId") Long sessionId);

    List<AssistantMessageEntity> selectBySessionIdOrderByCreatedAt(@Param("sessionId") Long sessionId);

    List<AssistantMessageEntity> selectRecentBySessionId(
            @Param("sessionId") Long sessionId,
            @Param("limit") int limit
    );

    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
