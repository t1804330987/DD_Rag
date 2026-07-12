package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssistantInstructionProfileMapper {
    int insert(AssistantInstructionProfileEntity entity);
    List<AssistantInstructionProfileEntity> selectEnabledByUserId(@Param("userId") Long userId);
    List<AssistantInstructionProfileEntity> selectAllByUserId(@Param("userId") Long userId);
    AssistantInstructionProfileEntity selectByIdAndUserId(@Param("profileId") Long profileId,
                                                           @Param("userId") Long userId);
    AssistantInstructionProfileEntity selectDefaultEnabledByUserId(@Param("userId") Long userId);
    int updateCurrentVersion(@Param("profileId") Long profileId, @Param("userId") Long userId,
                             @Param("versionId") Long versionId, @Param("updatedAt") LocalDateTime updatedAt);
    int updateName(@Param("profileId") Long profileId, @Param("userId") Long userId,
                   @Param("name") String name, @Param("updatedAt") LocalDateTime updatedAt);
    int clearDefaultByUserId(@Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);
    int setDefault(@Param("profileId") Long profileId, @Param("userId") Long userId,
                   @Param("updatedAt") LocalDateTime updatedAt);
    int setEnabled(@Param("profileId") Long profileId, @Param("userId") Long userId,
                   @Param("enabled") boolean enabled, @Param("updatedAt") LocalDateTime updatedAt);
    int softDeleteByIdAndUserId(@Param("profileId") Long profileId, @Param("userId") Long userId,
                                @Param("deletedAt") LocalDateTime deletedAt);
}
