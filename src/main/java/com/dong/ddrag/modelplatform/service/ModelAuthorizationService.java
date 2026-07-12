package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionGrantMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionGrantEntity;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ConnectionStatus;
import com.dong.ddrag.modelplatform.model.enums.ModelGrantType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelAuthorizationService {
    private final ModelConnectionMapper connectionMapper;
    private final ModelConnectionGrantMapper grantMapper;

    public ModelAuthorizationService(ModelConnectionMapper connectionMapper, ModelConnectionGrantMapper grantMapper) {
        this.connectionMapper = connectionMapper;
        this.grantMapper = grantMapper;
    }

    @Transactional
    public GrantView replacePlatformGrants(Long connectionId, GrantReplacementCommand command) {
        if (connectionId == null || connectionId <= 0 || command == null) {
            throw new BusinessException("MODEL_GRANT_REQUEST_INVALID");
        }
        List<Long> userIds = normalizeUserIds(command.userIds());
        if (command.allBusinessUsers() && !userIds.isEmpty()) {
            throw new BusinessException("MODEL_GRANT_SCOPE_AMBIGUOUS");
        }
        ModelConnectionEntity connection = connectionMapper.selectPlatformByIdForUpdate(connectionId);
        if (connection == null) {
            throw new BusinessException("PLATFORM_MODEL_CONNECTION_NOT_FOUND");
        }
        if (!command.allBusinessUsers() && grantMapper.countActiveBusinessUsers(userIds) != userIds.size()) {
            throw new BusinessException("MODEL_GRANT_BUSINESS_USER_INVALID");
        }

        grantMapper.deleteByConnectionId(connectionId);
        if (command.allBusinessUsers()) {
            grantMapper.insert(grant(connectionId, ModelGrantType.ALL_BUSINESS_USERS, null));
        } else {
            userIds.forEach(userId -> grantMapper.insert(grant(connectionId, ModelGrantType.USER, userId)));
        }
        return new GrantView(connectionId, command.allBusinessUsers(), userIds);
    }

    public GrantView getPlatformGrants(Long connectionId) {
        if (connectionId == null || connectionId <= 0) {
            throw new BusinessException("MODEL_GRANT_REQUEST_INVALID");
        }
        if (connectionMapper.selectOwnedById(connectionId, "PLATFORM", null) == null) {
            throw new BusinessException("PLATFORM_MODEL_CONNECTION_NOT_FOUND");
        }
        List<ModelConnectionGrantEntity> grants = grantMapper.selectByConnectionId(connectionId);
        boolean allBusinessUsers = grants.stream()
                .anyMatch(grant -> ModelGrantType.ALL_BUSINESS_USERS.name().equals(grant.getGrantType()));
        List<Long> userIds = allBusinessUsers ? List.of() : grants.stream()
                .filter(grant -> ModelGrantType.USER.name().equals(grant.getGrantType()))
                .map(ModelConnectionGrantEntity::getGranteeUserId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return new GrantView(connectionId, allBusinessUsers, userIds);
    }

    public boolean isAuthorized(Long userId, ModelConnectionEntity connection) {
        if (userId == null || connection == null || connection.getId() == null
                || !grantMapper.existsActiveBusinessUser(userId)) {
            return false;
        }
        ModelConnectionEntity current = connectionMapper.selectFormalById(connection.getId());
        if (current == null || !ConnectionStatus.ACTIVE.name().equals(current.getStatus())
                || current.getDeletedAt() != null) {
            return false;
        }
        ConnectionOwnerType ownerType;
        try {
            ownerType = ConnectionOwnerType.valueOf(current.getOwnerType());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
        return switch (ownerType) {
            case PLATFORM -> grantMapper.existsGrantForUser(current.getId(), userId);
            case USER -> Objects.equals(current.getOwnerUserId(), userId);
        };
    }

    public void requireAuthorized(Long userId, ModelConnectionEntity connection) {
        if (!isAuthorized(userId, connection)) {
            throw new BusinessException("MODEL_NOT_AUTHORIZED");
        }
    }

    private static List<Long> normalizeUserIds(List<Long> userIds) {
        if (userIds == null) return List.of();
        if (userIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException("MODEL_GRANT_BUSINESS_USER_INVALID");
        }
        return userIds.stream().distinct().sorted().toList();
    }

    private static ModelConnectionGrantEntity grant(Long connectionId, ModelGrantType type, Long userId) {
        ModelConnectionGrantEntity entity = new ModelConnectionGrantEntity();
        entity.setConnectionId(connectionId);
        entity.setGrantType(type.name());
        entity.setGranteeUserId(userId);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    public record GrantReplacementCommand(boolean allBusinessUsers, List<Long> userIds) { }
    public record GrantView(Long connectionId, boolean allBusinessUsers, List<Long> userIds) { }
}
