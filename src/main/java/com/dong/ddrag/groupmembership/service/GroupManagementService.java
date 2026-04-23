package com.dong.ddrag.groupmembership.service;

import com.dong.ddrag.common.enums.GroupInvitationStatus;
import com.dong.ddrag.common.enums.GroupRole;
import com.dong.ddrag.common.enums.GroupStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.mapper.GroupJoinRequestMapper;
import com.dong.ddrag.groupmembership.mapper.GroupMembershipMapper;
import com.dong.ddrag.groupmembership.model.dto.CreateGroupRequest;
import com.dong.ddrag.groupmembership.model.dto.CreateInvitationRequest;
import com.dong.ddrag.groupmembership.model.vo.GroupMemberVO;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GroupManagementService {

    private static final int MAX_GROUP_NAME_LENGTH = 128;
    private static final int MAX_GROUP_DESCRIPTION_LENGTH = 512;
    private final GroupMembershipMapper groupMembershipMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;

    public GroupManagementService(
            GroupMembershipMapper groupMembershipMapper,
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipMapper = groupMembershipMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Long createGroup(HttpServletRequest request, CreateGroupRequest createGroupRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String groupName = requireGroupName(createGroupRequest.getName());
        String description = normalizeDescription(createGroupRequest.getDescription());
        Long groupId = groupMembershipMapper.insertGroupReturningId(
                buildGroupCode(),
                groupName,
                description,
                currentUser.userId(),
                GroupStatus.ACTIVE.name()
        );
        groupMembershipMapper.insertMembership(groupId, currentUser.userId(), GroupRole.OWNER.name());
        return groupId;
    }

    @Transactional
    public Long createInvitation(
            HttpServletRequest request,
            Long groupId,
            CreateInvitationRequest createInvitationRequest
    ) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        Long inviteeUserId = requirePositiveId(createInvitationRequest.getInviteeUserId(), "被邀请用户非法");
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(request, requiredGroupId);
        rejectMissingUser(inviteeUserId);
        rejectDuplicateInvitationTarget(requiredGroupId, inviteeUserId);
        return groupMembershipMapper.insertPendingInvitationReturningId(
                requiredGroupId,
                currentUser.userId(),
                inviteeUserId,
                GroupInvitationStatus.PENDING.name()
        );
    }

    @Transactional
    public void acceptInvitation(HttpServletRequest request, Long invitationId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        Invitation invitation = loadInvitation(invitationId);
        requireInvitee(currentUser, invitation);
        requirePending(invitation);
        rejectExistingMembership(invitation.groupId(), invitation.inviteeUserId());
        groupMembershipMapper.insertMembership(
                invitation.groupId(),
                invitation.inviteeUserId(),
                GroupRole.MEMBER.name()
        );
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.ACCEPTED);
    }

    @Transactional
    public void rejectInvitation(HttpServletRequest request, Long invitationId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        Invitation invitation = loadInvitation(invitationId);
        requireInvitee(currentUser, invitation);
        requirePending(invitation);
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.REJECTED);
    }

    @Transactional
    public void cancelInvitation(HttpServletRequest request, Long invitationId) {
        Invitation invitation = loadInvitation(invitationId);
        groupMembershipService.requireGroupOwner(request, invitation.groupId());
        requirePending(invitation);
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.CANCELED);
    }

    public List<GroupMemberVO> listMembers(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        groupMembershipService.requireGroupOwner(request, requiredGroupId);
        return groupMembershipMapper.selectMembersByGroupId(requiredGroupId).stream()
                .map(this::toGroupMember)
                .toList();
    }

    @Transactional
    public void removeMember(HttpServletRequest request, Long groupId, Long userId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        Long requiredUserId = requirePositiveId(userId, "成员用户非法");
        groupMembershipService.requireGroupOwner(request, requiredGroupId);
        String role = groupMembershipMapper.selectActiveMembershipRole(requiredUserId, requiredGroupId);
        if (role == null) {
            throw new BusinessException("成员不存在");
        }
        if (GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException("不能移除 OWNER");
        }
        groupMembershipMapper.deleteMembership(requiredGroupId, requiredUserId);
    }

    @Transactional
    public void leaveGroup(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requiredGroupId);
        if (role == null) {
            throw new BusinessException("当前用户不是目标群组成员");
        }
        if (GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException("OWNER 不能退出自己的组");
        }
        groupMembershipMapper.deleteMembership(requiredGroupId, currentUser.userId());
    }

    private void rejectDuplicateInvitationTarget(Long groupId, Long inviteeUserId) {
        rejectExistingMembership(groupId, inviteeUserId);
        if (hasRows(groupMembershipMapper.countPendingInvitation(groupId, inviteeUserId))) {
            throw new BusinessException("已存在待处理邀请");
        }
        if (hasRows(groupJoinRequestMapper.countPendingJoinRequest(groupId, inviteeUserId))) {
            throw new BusinessException("该用户已有待处理加入申请，请先审批申请");
        }
    }

    private void rejectExistingMembership(Long groupId, Long inviteeUserId) {
        if (hasRows(groupMembershipMapper.countMembershipByGroupIdAndUserId(groupId, inviteeUserId))) {
            throw new BusinessException("被邀请人已是群组成员");
        }
    }

    private void rejectMissingUser(Long userId) {
        if (!hasRows(groupMembershipMapper.countUserById(userId))) {
            throw new BusinessException("被邀请用户不存在");
        }
    }

    private Invitation loadInvitation(Long invitationId) {
        Long requiredInvitationId = requirePositiveId(invitationId, "邀请ID非法");
        Map<String, Object> row = groupMembershipMapper.selectInvitationById(requiredInvitationId);
        if (row == null) {
            throw new BusinessException("邀请不存在");
        }
        return new Invitation(
                toLong(row.get("invitationId")),
                toLong(row.get("groupId")),
                toLong(row.get("inviterUserId")),
                toLong(row.get("inviteeUserId")),
                String.valueOf(row.get("status"))
        );
    }

    private void requireInvitee(CurrentUserService.CurrentUser currentUser, Invitation invitation) {
        if (!currentUser.userId().equals(invitation.inviteeUserId())) {
            throw new BusinessException("无权处理该邀请");
        }
    }

    private void requirePending(Invitation invitation) {
        if (!GroupInvitationStatus.PENDING.name().equals(invitation.status())) {
            throw new BusinessException("邀请已处理");
        }
    }

    private void updateInvitationStatus(Long invitationId, GroupInvitationStatus status) {
        int updated = groupMembershipMapper.updateInvitationStatus(
                invitationId,
                GroupInvitationStatus.PENDING.name(),
                status.name()
        );
        if (updated == 0) {
            throw new BusinessException("邀请已处理");
        }
    }

    private GroupMemberVO toGroupMember(Map<String, Object> row) {
        return new GroupMemberVO(
                toLong(row.get("userId")),
                String.valueOf(row.get("userCode")),
                String.valueOf(row.get("displayName")),
                String.valueOf(row.get("role"))
        );
    }

    private String requireGroupName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("组名称不能为空");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_GROUP_NAME_LENGTH) {
            throw new BusinessException("组名称不能超过 128");
        }
        return trimmedName;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String trimmedDescription = description.trim();
        if (trimmedDescription.length() > MAX_GROUP_DESCRIPTION_LENGTH) {
            throw new BusinessException("组描述不能超过 512");
        }
        return trimmedDescription;
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(message);
        }
        return id;
    }

    private String buildGroupCode() {
        return "group-" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean hasRows(Long count) {
        return count != null && count > 0;
    }

    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private record Invitation(
            Long id,
            Long groupId,
            Long inviterUserId,
            Long inviteeUserId,
            String status
    ) {
    }
}
