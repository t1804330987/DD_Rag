package com.dong.ddrag.modelplatform.mapper;

import com.dong.ddrag.modelplatform.model.entity.ModelScenarioRouteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModelScenarioRouteMapper {
    int insert(ModelScenarioRouteEntity entity);
    int upsert(ModelScenarioRouteEntity entity);
    ModelScenarioRouteEntity selectFormalRouteByScenario(@Param("scenario") String scenario);
}
