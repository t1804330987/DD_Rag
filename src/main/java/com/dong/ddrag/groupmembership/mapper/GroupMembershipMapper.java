package com.dong.ddrag.groupmembership.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMembershipMapper {

    List<Map<String, Object>> selectOwnedGroupsByUserId(@Param("userId") Long userId);

    List<Map<String, Object>> selectJoinedGroupsByUserId(@Param("userId") Long userId);

    List<Map<String, Object>> selectPendingInvitationsByInviteeUserId(@Param("inviteeUserId") Long inviteeUserId);

    String selectActiveMembershipRole(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId
    );

    Long countActiveMembership(
            @Param("userId") Long userId,
            @Param("groupId") Long groupId
    );

    Long countMembershipByGroupIdAndUserId(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    Long countPendingInvitation(
            @Param("groupId") Long groupId,
            @Param("inviteeUserId") Long inviteeUserId
    );

    Long countUserById(@Param("userId") Long userId);

    Long insertGroupReturningId(
            @Param("groupCode") String groupCode,
            @Param("groupName") String groupName,
            @Param("description") String description,
            @Param("ownerUserId") Long ownerUserId,
            @Param("status") String status
    );

    int insertMembership(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId,
            @Param("role") String role
    );

    int insertPendingInvitation(
            @Param("groupId") Long groupId,
            @Param("inviterUserId") Long inviterUserId,
            @Param("inviteeUserId") Long inviteeUserId,
            @Param("status") String status
    );

    Long insertPendingInvitationReturningId(
            @Param("groupId") Long groupId,
            @Param("inviterUserId") Long inviterUserId,
            @Param("inviteeUserId") Long inviteeUserId,
            @Param("status") String status
    );

    Map<String, Object> selectInvitationById(@Param("invitationId") Long invitationId);

    int updateInvitationStatus(
            @Param("invitationId") Long invitationId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus
    );

    List<Map<String, Object>> selectMembersByGroupId(@Param("groupId") Long groupId);

    int deleteMembership(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );
}
