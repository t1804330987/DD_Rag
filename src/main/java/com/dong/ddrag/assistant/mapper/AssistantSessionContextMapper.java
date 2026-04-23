package com.dong.ddrag.assistant.mapper;

import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssistantSessionContextMapper {

    AssistantSessionContextEntity selectBySessionId(@Param("sessionId") Long sessionId);

    int upsert(AssistantSessionContextEntity assistantSessionContextEntity);

    int updateShortTermMemoryWithVersion(
            @Param("context") AssistantSessionContextEntity assistantSessionContextEntity,
            @Param("expectedVersion") Long expectedVersion
    );

    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
