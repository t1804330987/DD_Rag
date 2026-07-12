package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ModelConnectionModelMapper {
    int insert(ModelConnectionModelEntity entity);
    ModelConnectionModelEntity selectById(@Param("modelId") Long modelId, @Param("connectionId") Long connectionId);
    List<ModelConnectionModelEntity> selectByConnectionId(@Param("connectionId") Long connectionId);
    ModelConnectionModelEntity selectFormalById(@Param("modelId") Long modelId, @Param("connectionId") Long connectionId);
    List<ModelConnectionModelEntity> selectFormalByConnectionId(@Param("connectionId") Long connectionId);
    int upsertCatalogModel(ModelConnectionModelEntity entity);
    int invalidateTests(@Param("connectionId") Long connectionId, @Param("updatedAt") java.time.LocalDateTime updatedAt);
    int completeTestCas(@Param("modelId") Long modelId, @Param("connectionId") Long connectionId,
            @Param("configVersion") Long configVersion, @Param("testStatus") String testStatus,
            @Param("testedAt") java.time.LocalDateTime testedAt);
    int updateEnabled(@Param("modelId") Long modelId, @Param("connectionId") Long connectionId,
            @Param("configVersion") Long configVersion, @Param("enabled") Boolean enabled,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);
    int hideById(@Param("modelId") Long modelId, @Param("connectionId") Long connectionId,
                 @Param("hiddenAt") java.time.LocalDateTime hiddenAt);
}
