package com.dong.ddrag.groupmembership.service;

import com.dong.ddrag.common.enums.GroupJoinRequestStatus;
import com.dong.ddrag.common.enums.GroupRole;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.mapper.GroupJoinRequestMapper;
import com.dong.ddrag.groupmembership.model.dto.CreateJoinRequestRequest;
import com.dong.ddrag.groupmembership.model.vo.MyJoinRequestVO;
import com.dong.ddrag.groupmembership.model.vo.OwnerJoinRequestVO;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Service
public class GroupJoinRequestService {

    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;

    public GroupJoinRequestService(
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService
    ) {
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Long submitJoinRequest(HttpServletRequest request, CreateJoinRequestRequest joinRequestRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        String groupCode = requireGroupCode(joinRequestRequest.getGroupCode());
        GroupSummary group = loadGroupByCode(groupCode);
        rejectExistingMembership(group.groupId(), currentUser.userId(), "该用户已经是知识库成员");
        if (hasRows(groupJoinRequestMapper.countPendingInvitation(group.groupId(), currentUser.userId()))) {
            throw new BusinessException("该知识库已有待处理邀请，请先处理邀请");
        }
        if (hasRows(groupJoinRequestMapper.countPendingJoinRequest(group.groupId(), currentUser.userId()))) {
            throw new BusinessException("该用户已有待处理加入申请，请先等待审批");
        }
        return groupJoinRequestMapper.insertPendingJoinRequestReturningId(
                group.groupId(),
                currentUser.userId(),
                GroupJoinRequestStatus.PENDING.name()
        );
    }

    public List<MyJoinRequestVO> listMyJoinRequests(HttpServletRequest request) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return groupJoinRequestMapper.selectMyJoinRequests(currentUser.userId()).stream()
                .map(this::toMyJoinRequest)
                .toList();
    }

    public List<OwnerJoinRequestVO> listOwnerJoinRequests(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        groupMembershipService.requireGroupOwner(request, requiredGroupId);
        return groupJoinRequestMapper.selectPendingJoinRequestsByGroupId(requiredGroupId).stream()
                .map(this::toOwnerJoinRequest)
                .toList();
    }

    @Transactional
    public void approveJoinRequest(HttpServletRequest request, Long groupId, Long requestId) {
        CurrentUserService.CurrentUser owner = requireOwnerAndLoadUser(request, groupId);
        JoinRequest joinRequest = loadJoinRequest(requestId);
        requireSameGroup(groupId, joinRequest);
        requirePending(joinRequest);
        rejectExistingMembership(joinRequest.groupId(), joinRequest.applicantUserId(), "该用户已经是知识库成员");
        groupJoinRequestMapper.insertMembership(
                joinRequest.groupId(),
                joinRequest.applicantUserId(),
                GroupRole.MEMBER.name()
        );
        updateStatus(joinRequest.requestId(), GroupJoinRequestStatus.APPROVED, owner.userId());
    }

    @Transactional
    public void rejectJoinRequest(HttpServletRequest request, Long groupId, Long requestId) {
        CurrentUserService.CurrentUser owner = requireOwnerAndLoadUser(request, groupId);
        JoinRequest joinRequest = loadJoinRequest(requestId);
        requireSameGroup(groupId, joinRequest);
        requirePending(joinRequest);
        updateStatus(joinRequest.requestId(), GroupJoinRequestStatus.REJECTED, owner.userId());
    }

    private CurrentUserService.CurrentUser requireOwnerAndLoadUser(HttpServletRequest request, Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        return groupMembershipService.requireGroupOwner(request, requiredGroupId);
    }

    private String requireGroupCode(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            throw new BusinessException("组织 ID 不能为空");
        }
        return groupCode.trim();
    }

    private GroupSummary loadGroupByCode(String groupCode) {
        Map<String, Object> row = groupJoinRequestMapper.selectActiveGroupByCode(groupCode);
        if (row == null) {
            throw new BusinessException("组织 ID 不存在");
        }
        return new GroupSummary(toLong(row.get("groupId")));
    }

    private JoinRequest loadJoinRequest(Long requestId) {
        Long requiredRequestId = requirePositiveId(requestId, "申请ID非法");
        Map<String, Object> row = groupJoinRequestMapper.selectJoinRequestById(requiredRequestId);
        if (row == null) {
            throw new BusinessException("申请不存在");
        }
        return new JoinRequest(
                toLong(row.get("requestId")),
                toLong(row.get("groupId")),
                toLong(row.get("applicantUserId")),
                String.valueOf(row.get("status"))
        );
    }

    private void rejectExistingMembership(Long groupId, Long userId, String message) {
        if (hasRows(groupJoinRequestMapper.countActiveMembership(groupId, userId))) {
            throw new BusinessException(message);
        }
    }

    private void requireSameGroup(Long groupId, JoinRequest joinRequest) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        if (!requiredGroupId.equals(joinRequest.groupId())) {
            throw new BusinessException("申请不存在");
        }
    }

    private void requirePending(JoinRequest joinRequest) {
        if (!GroupJoinRequestStatus.PENDING.name().equals(joinRequest.status())) {
            throw new BusinessException("申请已处理");
        }
    }

    private void updateStatus(Long requestId, GroupJoinRequestStatus status, Long decidedByUserId) {
        int updated = groupJoinRequestMapper.updateJoinRequestStatus(
                requestId,
                GroupJoinRequestStatus.PENDING.name(),
                status.name(),
                decidedByUserId
        );
        if (updated == 0) {
            throw new BusinessException("申请已处理");
        }
    }

    private MyJoinRequestVO toMyJoinRequest(Map<String, Object> row) {
        return new MyJoinRequestVO(
                toLong(row.get("requestId")),
                toLong(row.get("groupId")),
                String.valueOf(row.get("groupCode")),
                String.valueOf(row.get("groupName")),
                String.valueOf(row.get("status")),
                toLocalDateTime(row.get("createdAt")),
                toNullableLocalDateTime(row.get("decidedAt"))
        );
    }

    private OwnerJoinRequestVO toOwnerJoinRequest(Map<String, Object> row) {
        return new OwnerJoinRequestVO(
                toLong(row.get("requestId")),
                toLong(row.get("groupId")),
                toLong(row.get("applicantUserId")),
                String.valueOf(row.get("applicantUserCode")),
                String.valueOf(row.get("applicantDisplayName")),
                String.valueOf(row.get("status")),
                toLocalDateTime(row.get("createdAt"))
        );
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(message);
        }
        return id;
    }

    private boolean hasRows(Long count) {
        return count != null && count > 0;
    }

    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    private LocalDateTime toNullableLocalDateTime(Object value) {
        return value == null ? null : toLocalDateTime(value);
    }

    private record GroupSummary(Long groupId) {
    }

    private record JoinRequest(Long requestId, Long groupId, Long applicantUserId, String status) {
    }
}
