package com.dong.ddrag.harness.grouppermission;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.common.exception.ForbiddenException;
import com.dong.ddrag.groupmembership.mapper.GroupJoinRequestMapper;
import com.dong.ddrag.groupmembership.mapper.GroupMembershipMapper;
import com.dong.ddrag.groupmembership.model.dto.CreateGroupRequest;
import com.dong.ddrag.groupmembership.service.GroupJoinRequestService;
import com.dong.ddrag.groupmembership.service.GroupManagementService;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class GroupPermissionHarnessTest {

    private static final Long USER_ID = 1001L;
    private static final Long GROUP_ID = 2001L;

    @Test
    void businessUserCreatesGroupAndBecomesOwner() {
        HarnessRuntime runtime = createRuntime();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CreateGroupRequest createGroupRequest = new CreateGroupRequest();
        createGroupRequest.setName(" 产品团队资料库 ");
        createGroupRequest.setDescription(" 沉淀产品规范 ");
        given(runtime.currentUserService().requireBusinessUser(request))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        given(runtime.groupMembershipMapper().insertGroupReturningId(
                anyString(),
                eq("产品团队资料库"),
                eq("沉淀产品规范"),
                eq(USER_ID),
                eq("ACTIVE")
        )).willReturn(GROUP_ID);

        Long groupId = runtime.groupManagementService().createGroup(request, createGroupRequest);

        assertThat(groupId).isEqualTo(GROUP_ID);
        then(runtime.groupMembershipMapper()).should().insertGroupReturningId(
                anyString(),
                eq("产品团队资料库"),
                eq("沉淀产品规范"),
                eq(USER_ID),
                eq("ACTIVE")
        );
        then(runtime.groupMembershipMapper()).should().insertMembership(GROUP_ID, USER_ID, "OWNER");
    }

    @Test
    void systemAdminCannotCreateBusinessGroupAndProducesNoGroupSideEffects() {
        HarnessRuntime runtime = createRuntime();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CreateGroupRequest createGroupRequest = new CreateGroupRequest();
        createGroupRequest.setName("管理员业务组");
        given(runtime.currentUserService().requireBusinessUser(request))
                .willThrow(new ForbiddenException("系统管理员不能访问普通业务区"));

        assertThatThrownBy(() -> runtime.groupManagementService().createGroup(request, createGroupRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("系统管理员不能访问普通业务区");

        then(runtime.groupMembershipMapper()).should(never())
                .insertGroupReturningId(anyString(), anyString(), any(), any(), anyString());
        then(runtime.groupMembershipMapper()).should(never()).insertMembership(any(), any(), anyString());
    }

    @Test
    void memberCanReadGroupButCannotUseOwnerPermission() {
        HarnessRuntime runtime = createRuntime();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(runtime.currentUserService().requireBusinessUser(request))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        given(runtime.groupMembershipMapper().selectActiveMembershipRole(USER_ID, GROUP_ID)).willReturn("MEMBER");

        CurrentUserService.CurrentUser readableUser =
                runtime.groupMembershipService().requireGroupReadable(request, GROUP_ID);

        assertThat(readableUser.userId()).isEqualTo(USER_ID);
        assertThatThrownBy(() -> runtime.groupMembershipService().requireGroupOwner(request, GROUP_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户不是目标群组 OWNER");
    }

    @Test
    void ownerApprovesJoinRequestCreatesMemberAndMarksRequestApproved() {
        HarnessRuntime runtime = createRuntime();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(runtime.currentUserService().requireBusinessUser(request))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        given(runtime.groupMembershipMapper().selectActiveMembershipRole(USER_ID, GROUP_ID)).willReturn("OWNER");
        given(runtime.groupJoinRequestMapper().selectJoinRequestById(7001L)).willReturn(Map.of(
                "requestId", 7001L,
                "groupId", GROUP_ID,
                "applicantUserId", 1002L,
                "status", "PENDING"
        ));
        given(runtime.groupJoinRequestMapper().countActiveMembership(GROUP_ID, 1002L)).willReturn(0L);
        given(runtime.groupJoinRequestMapper().updateJoinRequestStatus(7001L, "PENDING", "APPROVED", USER_ID))
                .willReturn(1);

        runtime.groupJoinRequestService().approveJoinRequest(request, GROUP_ID, 7001L);

        then(runtime.groupJoinRequestMapper()).should().insertMembership(GROUP_ID, 1002L, "MEMBER");
        then(runtime.groupJoinRequestMapper()).should().updateJoinRequestStatus(7001L, "PENDING", "APPROVED", USER_ID);
    }

    @Test
    void nonMemberCannotReadGroupScopedBusinessData() {
        HarnessRuntime runtime = createRuntime();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(runtime.currentUserService().requireBusinessUser(request))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        given(runtime.groupMembershipMapper().selectActiveMembershipRole(USER_ID, GROUP_ID)).willReturn(null);

        assertThatThrownBy(() -> runtime.groupMembershipService().requireGroupReadable(request, GROUP_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户不是目标群组成员");
    }

    private HarnessRuntime createRuntime() {
        GroupMembershipMapper groupMembershipMapper = mock(GroupMembershipMapper.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        GroupMembershipService groupMembershipService = new GroupMembershipService(
                groupMembershipMapper,
                currentUserService
        );
        GroupManagementService groupManagementService = new GroupManagementService(
                groupMembershipMapper,
                groupJoinRequestMapper,
                groupMembershipService,
                currentUserService
        );
        GroupJoinRequestService groupJoinRequestService = new GroupJoinRequestService(
                groupJoinRequestMapper,
                groupMembershipService,
                currentUserService
        );
        return new HarnessRuntime(
                groupManagementService,
                groupJoinRequestService,
                groupMembershipService,
                groupJoinRequestMapper,
                groupMembershipMapper,
                currentUserService
        );
    }

    private record HarnessRuntime(
            GroupManagementService groupManagementService,
            GroupJoinRequestService groupJoinRequestService,
            GroupMembershipService groupMembershipService,
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupMembershipMapper groupMembershipMapper,
            CurrentUserService currentUserService
    ) {
    }
}
