package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ModelConnectionMapper {
    int insert(ModelConnectionEntity entity);
    ModelConnectionEntity selectOwnedById(@Param("connectionId") Long connectionId,
            @Param("ownerType") String ownerType, @Param("ownerUserId") Long ownerUserId);
    ModelConnectionEntity selectFormalById(@Param("connectionId") Long connectionId);
    ModelConnectionEntity selectPlatformByIdForUpdate(@Param("connectionId") Long connectionId);
    List<ModelConnectionEntity> selectByOwner(@Param("ownerType") String ownerType, @Param("ownerUserId") Long ownerUserId, @Param("status") String status);
    List<ModelConnectionEntity> selectAllByOwner(@Param("ownerType") String ownerType,
            @Param("ownerUserId") Long ownerUserId);
    int updateOwnedConfig(@Param("entity") ModelConnectionEntity entity,
            @Param("expectedConfigVersion") Long expectedConfigVersion,
            @Param("expectedStatus") String expectedStatus,
            @Param("updateApiKey") boolean updateApiKey);
    int updateOwnedStatus(@Param("connectionId") Long connectionId, @Param("ownerType") String ownerType,
            @Param("ownerUserId") Long ownerUserId, @Param("expectedStatus") String expectedStatus,
            @Param("expectedConfigVersion") Long expectedConfigVersion,
            @Param("newStatus") String newStatus, @Param("updatedAt") LocalDateTime updatedAt);
    int completeConnectionTestCas(@Param("connectionId") Long connectionId,
            @Param("configVersion") Long configVersion, @Param("testStatus") String testStatus,
            @Param("connectionStatus") String connectionStatus, @Param("testedAt") LocalDateTime testedAt);
    int softDeleteOwned(@Param("connectionId") Long connectionId, @Param("ownerType") String ownerType,
            @Param("ownerUserId") Long ownerUserId, @Param("deletedAt") LocalDateTime deletedAt);
}
