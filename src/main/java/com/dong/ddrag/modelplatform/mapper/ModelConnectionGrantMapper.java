package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.ModelConnectionGrantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ModelConnectionGrantMapper {
    int insert(ModelConnectionGrantEntity entity);
    int deleteByConnectionId(@Param("connectionId") Long connectionId);
    List<ModelConnectionGrantEntity> selectByConnectionId(@Param("connectionId") Long connectionId);
    int countActiveBusinessUsers(@Param("userIds") List<Long> userIds);
    boolean existsActiveBusinessUser(@Param("userId") Long userId);
    boolean existsGrantForUser(@Param("connectionId") Long connectionId, @Param("userId") Long userId);
}
