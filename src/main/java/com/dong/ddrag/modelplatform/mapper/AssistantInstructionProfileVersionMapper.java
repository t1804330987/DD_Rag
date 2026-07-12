package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AssistantInstructionProfileVersionMapper {
    int insert(AssistantInstructionProfileVersionEntity entity);
    List<AssistantInstructionProfileVersionEntity> selectByProfileIdAndUserId(
            @Param("profileId") Long profileId, @Param("userId") Long userId);
    AssistantInstructionProfileVersionEntity selectCurrentByProfileIdAndUserId(
            @Param("profileId") Long profileId, @Param("userId") Long userId);
    Integer selectMaxVersionByProfileId(@Param("profileId") Long profileId);
}
