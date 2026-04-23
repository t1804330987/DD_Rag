package com.dong.ddrag.groupmembership.service;

import com.dong.ddrag.common.enums.GroupInvitationStatus;
import com.dong.ddrag.common.enums.GroupRole;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.mapper.GroupMembershipMapper;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GroupMembershipService {

    private static final String NON_MEMBER_MESSAGE = "当前用户不是目标群组成员";
    private static final String NON_OWNER_MESSAGE = "当前用户不是目标群组 OWNER";
    private static final String EXISTING_MEMBER_MESSAGE = "被邀请人已是群组成员";
    private static final String EXISTING_PENDING_INVITATION_MESSAGE = "已存在待处理邀请";
    private final GroupMembershipMapper groupMembershipMapper;
    private final CurrentUserService currentUserService;

    public GroupMembershipService(
            GroupMembershipMapper groupMembershipMapper,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipMapper = groupMembershipMapper;
        this.currentUserService = currentUserService;
    }

    public GroupQueryResult listVisibleGroups(HttpServletRequest request) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return new GroupQueryResult(
                toVisibleGroups(groupMembershipMapper.selectOwnedGroupsByUserId(currentUser.userId())),
                toVisibleGroups(groupMembershipMapper.selectJoinedGroupsByUserId(currentUser.userId())),
                toPendingInvitations(groupMembershipMapper.selectPendingInvitationsByInviteeUserId(currentUser.userId()))
        );
    }

    public CurrentUserService.CurrentUser requireCurrentUserMember(HttpServletRequest request, Long groupId) {
        return requireGroupReadable(request, groupId);
    }

    public CurrentUserService.CurrentUser requireGroupReadable(HttpServletRequest request, Long groupId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requireGroupId(groupId));
        if (role == null) {
            throw new BusinessException(NON_MEMBER_MESSAGE);
        }
        return currentUser;
    }

    public CurrentUserService.CurrentUser requireGroupOwner(HttpServletRequest request, Long groupId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requireGroupId(groupId));
        if (role == null) {
            throw new BusinessException(NON_MEMBER_MESSAGE);
        }
        if (!GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException(NON_OWNER_MESSAGE);
        }
        return currentUser;
    }

    @Transactional
    public void createPendingInvitation(HttpServletRequest request, Long groupId, Long inviteeUserId) {
        Long requiredGroupId = requireGroupId(groupId);
        Long requiredInviteeUserId = requireUserId(inviteeUserId);
        CurrentUserService.CurrentUser currentUser = requireGroupOwner(request, requiredGroupId);
        rejectDuplicateInvitationTarget(requiredGroupId, requiredInviteeUserId);
        groupMembershipMapper.insertPendingInvitation(
                requiredGroupId,
                currentUser.userId(),
                requiredInviteeUserId,
                GroupInvitationStatus.PENDING.name()
        );
    }

    private void rejectDuplicateInvitationTarget(Long groupId, Long inviteeUserId) {
        if (hasRows(groupMembershipMapper.countMembershipByGroupIdAndUserId(groupId, inviteeUserId))) {
            throw new BusinessException(EXISTING_MEMBER_MESSAGE);
        }
        if (hasRows(groupMembershipMapper.countPendingInvitation(groupId, inviteeUserId))) {
            throw new BusinessException(EXISTING_PENDING_INVITATION_MESSAGE);
        }
    }

    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("被邀请用户非法");
        }
        return userId;
    }

    private boolean hasRows(Long count) {
        return count != null && count > 0;
    }

    private List<VisibleGroup> toVisibleGroups(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toVisibleGroup).toList();
    }

    private List<PendingInvitation> toPendingInvitations(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toPendingInvitation).toList();
    }

    private VisibleGroup toVisibleGroup(Map<String, Object> row) {
        Number groupId = (Number) row.get("groupId");
        return new VisibleGroup(groupId.longValue(), String.valueOf(row.get("groupCode")), String.valueOf(row.get("groupName")));
    }

    private PendingInvitation toPendingInvitation(Map<String, Object> row) {
        return new PendingInvitation(
                toLong(row.get("invitationId")),
                toLong(row.get("groupId")),
                String.valueOf(row.get("groupName")),
                toLong(row.get("inviterUserId")),
                String.valueOf(row.get("inviterDisplayName")),
                String.valueOf(row.get("status"))
        );
    }

    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    public record GroupQueryResult(
            List<VisibleGroup> ownedGroups,
            List<VisibleGroup> joinedGroups,
            List<PendingInvitation> pendingInvitations
    ) {
    }

    public record VisibleGroup(Long groupId, String groupCode, String groupName) {
    }

    public record PendingInvitation(
            Long invitationId,
            Long groupId,
            String groupName,
            Long inviterUserId,
            String inviterDisplayName,
            String status
    ) {
    }
}
