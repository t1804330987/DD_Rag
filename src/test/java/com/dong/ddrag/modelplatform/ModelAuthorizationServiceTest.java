package com.dong.ddrag.modelplatform;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionGrantMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionGrantEntity;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelAuthorizationServiceTest {
    private ModelConnectionMapper connectionMapper;
    private ModelConnectionGrantMapper grantMapper;
    private ModelAuthorizationService service;

    @BeforeEach
    void setUp() {
        connectionMapper = mock(ModelConnectionMapper.class);
        grantMapper = mock(ModelConnectionGrantMapper.class);
        service = new ModelAuthorizationService(connectionMapper, grantMapper);
    }

    @Test
    void shouldReplaceWithAllBusinessUsersWithoutMixedUserGrants() {
        when(connectionMapper.selectPlatformByIdForUpdate(11L)).thenReturn(connection(11L, "PLATFORM", null));

        service.replacePlatformGrants(11L,
                new ModelAuthorizationService.GrantReplacementCommand(true, List.of()));

        InOrder order = inOrder(grantMapper);
        order.verify(grantMapper).deleteByConnectionId(11L);
        ArgumentCaptor<ModelConnectionGrantEntity> captor = ArgumentCaptor.forClass(ModelConnectionGrantEntity.class);
        order.verify(grantMapper).insert(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo("ALL_BUSINESS_USERS");
        assertThat(captor.getValue().getGranteeUserId()).isNull();
    }

    @Test
    void shouldReplaceWithDistinctSpecifiedBusinessUsers() {
        when(connectionMapper.selectPlatformByIdForUpdate(11L)).thenReturn(connection(11L, "PLATFORM", null));
        when(grantMapper.countActiveBusinessUsers(List.of(1001L, 1002L))).thenReturn(2);

        service.replacePlatformGrants(11L,
                new ModelAuthorizationService.GrantReplacementCommand(false, List.of(1002L, 1001L, 1002L)));

        verify(grantMapper).deleteByConnectionId(11L);
        ArgumentCaptor<ModelConnectionGrantEntity> captor = ArgumentCaptor.forClass(ModelConnectionGrantEntity.class);
        verify(grantMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(ModelConnectionGrantEntity::getGranteeUserId)
                .containsExactly(1001L, 1002L);
        assertThat(captor.getAllValues()).allMatch(grant -> "USER".equals(grant.getGrantType()));
    }

    @Test
    void shouldAllowEmptySpecifiedUsersToRevokeAllAccess() {
        when(connectionMapper.selectPlatformByIdForUpdate(11L)).thenReturn(connection(11L, "PLATFORM", null));

        ModelAuthorizationService.GrantView result = service.replacePlatformGrants(11L,
                new ModelAuthorizationService.GrantReplacementCommand(false, List.of()));

        verify(grantMapper).deleteByConnectionId(11L);
        verify(grantMapper, never()).insert(any());
        assertThat(result.allBusinessUsers()).isFalse();
        assertThat(result.userIds()).isEmpty();
    }

    @Test
    void shouldReadCurrentPlatformGrantScopeWithoutConnectionCredentials() {
        when(connectionMapper.selectOwnedById(11L, "PLATFORM", null))
                .thenReturn(connection(11L, "PLATFORM", null));
        ModelConnectionGrantEntity allUsers = new ModelConnectionGrantEntity();
        allUsers.setGrantType("ALL_BUSINESS_USERS");
        ModelConnectionGrantEntity user = new ModelConnectionGrantEntity();
        user.setGrantType("USER");
        user.setGranteeUserId(1002L);
        when(grantMapper.selectByConnectionId(11L)).thenReturn(List.of(user, allUsers));

        ModelAuthorizationService.GrantView result = service.getPlatformGrants(11L);

        assertThat(result).isEqualTo(new ModelAuthorizationService.GrantView(11L, true, List.of()));
        verify(connectionMapper).selectOwnedById(11L, "PLATFORM", null);
    }

    @Test
    void shouldRejectMixedAllUsersAndSpecifiedUsers() {
        assertThatThrownBy(() -> service.replacePlatformGrants(11L,
                new ModelAuthorizationService.GrantReplacementCommand(true, List.of(1001L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("MODEL_GRANT_SCOPE_AMBIGUOUS");
        verify(grantMapper, never()).deleteByConnectionId(any());
    }

    @Test
    void shouldRejectByokCrossUserGrant() {
        when(connectionMapper.selectPlatformByIdForUpdate(12L)).thenReturn(null);

        assertThatThrownBy(() -> service.replacePlatformGrants(12L,
                new ModelAuthorizationService.GrantReplacementCommand(false, List.of(1001L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PLATFORM_MODEL_CONNECTION_NOT_FOUND");
        verify(grantMapper, never()).deleteByConnectionId(any());
    }

    @Test
    void shouldAuthorizeAllUsersSpecifiedUserAndByokOwnerButNotAdmin() {
        when(connectionMapper.selectFormalById(11L)).thenReturn(connection(11L, "PLATFORM", null));
        when(grantMapper.existsActiveBusinessUser(1001L)).thenReturn(true);
        when(grantMapper.existsGrantForUser(11L, 1001L)).thenReturn(true);
        assertThat(service.isAuthorized(1001L, connection(11L, "PLATFORM", null))).isTrue();

        when(grantMapper.existsActiveBusinessUser(1002L)).thenReturn(true);
        when(grantMapper.existsGrantForUser(11L, 1002L)).thenReturn(false);
        assertThat(service.isAuthorized(1002L, connection(11L, "PLATFORM", null))).isFalse();

        when(connectionMapper.selectFormalById(12L)).thenReturn(connection(12L, "USER", 1003L));
        when(grantMapper.existsActiveBusinessUser(1003L)).thenReturn(true);
        assertThat(service.isAuthorized(1003L, connection(12L, "USER", 1003L))).isTrue();
        assertThat(service.isAuthorized(1002L, connection(12L, "USER", 1003L))).isFalse();

        when(grantMapper.existsActiveBusinessUser(1L)).thenReturn(false);
        assertThat(service.isAuthorized(1L, connection(11L, "PLATFORM", null))).isFalse();
    }

    @Test
    void shouldRejectAuthorizationImmediatelyWhenConnectionIsNoLongerActive() {
        ModelConnectionEntity staleActive = connection(11L, "PLATFORM", null);
        when(grantMapper.existsActiveBusinessUser(1001L)).thenReturn(true);
        when(connectionMapper.selectFormalById(11L)).thenReturn(null);

        assertThat(service.isAuthorized(1001L, staleActive)).isFalse();
        verify(grantMapper, never()).existsGrantForUser(any(), any());
    }

    @Test
    void shouldRejectStaleByokSnapshotAfterConnectionIsDisabledOrDeleted() {
        ModelConnectionEntity staleActive = connection(12L, "USER", 1001L);
        when(grantMapper.existsActiveBusinessUser(1001L)).thenReturn(true);
        when(connectionMapper.selectFormalById(12L)).thenReturn(null);

        assertThat(service.isAuthorized(1001L, staleActive)).isFalse();
    }

    @Test
    void shouldObserveGrantRevocationOnTheNextAuthorizationCheck() {
        ModelConnectionEntity platform = connection(11L, "PLATFORM", null);
        when(connectionMapper.selectFormalById(11L)).thenReturn(platform);
        when(grantMapper.existsActiveBusinessUser(1001L)).thenReturn(true);
        when(grantMapper.existsGrantForUser(11L, 1001L)).thenReturn(true, false);

        assertThat(service.isAuthorized(1001L, platform)).isTrue();
        assertThat(service.isAuthorized(1001L, platform)).isFalse();
    }

    private static ModelConnectionEntity connection(Long id, String ownerType, Long ownerUserId) {
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setId(id);
        entity.setOwnerType(ownerType);
        entity.setOwnerUserId(ownerUserId);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
