package com.dong.ddrag.groupmembership.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupJoinRequestMapper {

    Map<String, Object> selectActiveGroupByCode(@Param("groupCode") String groupCode);

    Long countActiveMembership(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    Long countPendingInvitation(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    Long countPendingJoinRequest(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    Long insertPendingJoinRequestReturningId(
            @Param("groupId") Long groupId,
            @Param("applicantUserId") Long applicantUserId,
            @Param("status") String status
    );

    List<Map<String, Object>> selectMyJoinRequests(@Param("applicantUserId") Long applicantUserId);

    List<Map<String, Object>> selectPendingJoinRequestsByGroupId(@Param("groupId") Long groupId);

    Map<String, Object> selectJoinRequestById(@Param("requestId") Long requestId);

    int updateJoinRequestStatus(
            @Param("requestId") Long requestId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("decidedByUserId") Long decidedByUserId
    );

    int insertMembership(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId,
            @Param("role") String role
    );
}
