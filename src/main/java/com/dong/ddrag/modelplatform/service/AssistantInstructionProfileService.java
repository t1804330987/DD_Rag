package com.dong.ddrag.modelplatform.service;

import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.AssistantInstructionProfileMapper;
import com.dong.ddrag.modelplatform.mapper.AssistantInstructionProfileVersionMapper;
import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileEntity;
import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileVersionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns user-scoped instruction profiles; content is append-only through version rows. */
@Service
public class AssistantInstructionProfileService {
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_CONTENT_LENGTH = 8_000;

    private final AssistantInstructionProfileMapper profileMapper;
    private final AssistantInstructionProfileVersionMapper versionMapper;
    private final AssistantSessionMapper sessionMapper;

    public AssistantInstructionProfileService(AssistantInstructionProfileMapper profileMapper,
                                              AssistantInstructionProfileVersionMapper versionMapper,
                                              AssistantSessionMapper sessionMapper) {
        this.profileMapper = profileMapper;
        this.versionMapper = versionMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional
    public ProfileView create(Long userId, String name, String content, boolean makeDefault) {
        requireUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        AssistantInstructionProfileEntity profile = new AssistantInstructionProfileEntity();
        profile.setUserId(userId);
        profile.setName(normalizeName(name));
        profile.setEnabled(true);
        profile.setDefault(false);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        if (profileMapper.insert(profile) != 1 || profile.getId() == null) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_CREATE_FAILED");
        }
        AssistantInstructionProfileVersionEntity version = insertVersion(profile.getId(), 1, normalizeContent(content));
        if (profileMapper.updateCurrentVersion(profile.getId(), userId, version.getId(), now) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_CREATE_FAILED");
        }
        if (makeDefault) {
            setDefaultInternal(userId, profile.getId(), now);
            profile.setDefault(true);
        }
        return new ProfileView(profile.getId(), profile.getName(), true, profile.getDefault(),
                version.getId(), version.getVersion(), version.getContent());
    }

    @Transactional
    public ProfileView updateContent(Long userId, Long profileId, String content) {
        AssistantInstructionProfileEntity profile = requireOwnedProfile(userId, profileId);
        Integer maxVersion = versionMapper.selectMaxVersionByProfileId(profileId);
        AssistantInstructionProfileVersionEntity version = insertVersion(profileId,
                (maxVersion == null ? 0 : maxVersion) + 1, normalizeContent(content));
        if (profileMapper.updateCurrentVersion(profileId, userId, version.getId(), LocalDateTime.now()) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_UPDATE_FAILED");
        }
        return new ProfileView(profileId, profile.getName(), Boolean.TRUE.equals(profile.getEnabled()),
                Boolean.TRUE.equals(profile.getDefault()), version.getId(), version.getVersion(), version.getContent());
    }

    /** Updates a profile as one transaction so a rejected content change cannot leave a renamed profile behind. */
    @Transactional
    public ProfileView update(Long userId, Long profileId, String name, String content, boolean makeDefault) {
        String normalizedName = normalizeName(name);
        String normalizedContent = normalizeContent(content);
        AssistantInstructionProfileEntity profile = requireOwnedProfile(userId, profileId);
        LocalDateTime now = LocalDateTime.now();
        if (profileMapper.updateName(profileId, userId, normalizedName, now) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_UPDATE_FAILED");
        }
        Integer maxVersion = versionMapper.selectMaxVersionByProfileId(profileId);
        AssistantInstructionProfileVersionEntity version = insertVersion(profileId,
                (maxVersion == null ? 0 : maxVersion) + 1, normalizedContent);
        if (profileMapper.updateCurrentVersion(profileId, userId, version.getId(), now) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_UPDATE_FAILED");
        }
        if (makeDefault) {
            setDefaultInternal(userId, profileId, now);
        }
        return new ProfileView(profileId, normalizedName, Boolean.TRUE.equals(profile.getEnabled()),
                makeDefault || Boolean.TRUE.equals(profile.getDefault()), version.getId(), version.getVersion(),
                version.getContent());
    }

    @Transactional
    public void rename(Long userId, Long profileId, String name) {
        requireOwnedProfile(userId, profileId);
        if (profileMapper.updateName(profileId, userId, normalizeName(name), LocalDateTime.now()) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_UPDATE_FAILED");
        }
    }

    @Transactional
    public void setDefault(Long userId, Long profileId) {
        requireEnabledOwnedProfile(userId, profileId);
        setDefaultInternal(userId, profileId, LocalDateTime.now());
    }

    @Transactional
    public void setEnabled(Long userId, Long profileId, boolean enabled) {
        requireOwnedProfile(userId, profileId);
        if (profileMapper.setEnabled(profileId, userId, enabled, LocalDateTime.now()) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_UPDATE_FAILED");
        }
        if (!enabled) {
            sessionMapper.clearCurrentInstructionProfile(userId, profileId);
        }
    }

    @Transactional
    public void delete(Long userId, Long profileId) {
        requireOwnedProfile(userId, profileId);
        sessionMapper.clearCurrentInstructionProfile(userId, profileId);
        if (profileMapper.softDeleteByIdAndUserId(profileId, userId, LocalDateTime.now()) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_DELETE_FAILED");
        }
    }

    @Transactional(readOnly = true)
    public List<ProfileView> listEnabled(Long userId) {
        requireUserId(userId);
        return profileMapper.selectEnabledByUserId(userId).stream()
                .map(profile -> toView(profile, requireCurrentVersion(userId, profile.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileView> list(Long userId) {
        requireUserId(userId);
        return profileMapper.selectAllByUserId(userId).stream()
                .map(profile -> toView(profile, requireCurrentVersion(userId, profile.getId())))
                .toList();
    }

    @Transactional
    public ProfileView copy(Long userId, Long profileId, String name, boolean makeDefault) {
        AssistantInstructionProfileEntity source = requireOwnedProfile(userId, profileId);
        AssistantInstructionProfileVersionEntity version = requireCurrentVersion(userId, profileId);
        String copiedName = name == null || name.isBlank() ? source.getName() + " 副本" : name;
        return create(userId, copiedName, version.getContent(), makeDefault);
    }

    @Transactional(readOnly = true)
    public ResolvedInstruction resolveForSession(Long userId, Long profileId) {
        if (profileId == null) {
            return ResolvedInstruction.platformDefault();
        }
        AssistantInstructionProfileEntity profile = requireEnabledOwnedProfile(userId, profileId);
        return toResolved(profile, requireCurrentVersion(userId, profileId));
    }

    @Transactional(readOnly = true)
    public ResolvedInstruction resolveCurrentForSession(Long userId, Long sessionId) {
        requireUserId(userId);
        AssistantSessionEntity session = sessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException("ASSISTANT_SESSION_NOT_FOUND");
        }
        return resolveForSession(userId, session.getCurrentInstructionProfileId());
    }

    @Transactional(readOnly = true)
    public ResolvedInstruction resolveDefault(Long userId) {
        requireUserId(userId);
        AssistantInstructionProfileEntity profile = profileMapper.selectDefaultEnabledByUserId(userId);
        return profile == null ? ResolvedInstruction.platformDefault()
                : toResolved(profile, requireCurrentVersion(userId, profile.getId()));
    }

    private void setDefaultInternal(Long userId, Long profileId, LocalDateTime now) {
        profileMapper.clearDefaultByUserId(userId, now);
        if (profileMapper.setDefault(profileId, userId, now) != 1) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_DEFAULT_FAILED");
        }
    }

    private AssistantInstructionProfileVersionEntity insertVersion(Long profileId, int number, String content) {
        AssistantInstructionProfileVersionEntity version = new AssistantInstructionProfileVersionEntity();
        version.setProfileId(profileId);
        version.setVersion(number);
        version.setContent(content);
        version.setCreatedAt(LocalDateTime.now());
        if (versionMapper.insert(version) != 1 || version.getId() == null) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_VERSION_CREATE_FAILED");
        }
        return version;
    }

    private AssistantInstructionProfileEntity requireOwnedProfile(Long userId, Long profileId) {
        requireUserId(userId);
        if (profileId == null || profileId <= 0) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_NOT_FOUND");
        }
        AssistantInstructionProfileEntity profile = profileMapper.selectByIdAndUserId(profileId, userId);
        if (profile == null) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_NOT_FOUND");
        }
        return profile;
    }

    private AssistantInstructionProfileEntity requireEnabledOwnedProfile(Long userId, Long profileId) {
        AssistantInstructionProfileEntity profile = requireOwnedProfile(userId, profileId);
        if (!Boolean.TRUE.equals(profile.getEnabled())) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_NOT_AVAILABLE");
        }
        return profile;
    }

    private AssistantInstructionProfileVersionEntity requireCurrentVersion(Long userId, Long profileId) {
        AssistantInstructionProfileVersionEntity version = versionMapper.selectCurrentByProfileIdAndUserId(profileId, userId);
        if (version == null) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_VERSION_NOT_FOUND");
        }
        return version;
    }

    private ProfileView toView(AssistantInstructionProfileEntity profile, AssistantInstructionProfileVersionEntity version) {
        return new ProfileView(profile.getId(), profile.getName(), Boolean.TRUE.equals(profile.getEnabled()),
                Boolean.TRUE.equals(profile.getDefault()), version.getId(), version.getVersion(), version.getContent());
    }

    private ResolvedInstruction toResolved(AssistantInstructionProfileEntity profile,
                                           AssistantInstructionProfileVersionEntity version) {
        return new ResolvedInstruction(profile.getId(), version.getId(), version.getVersion(), version.getContent());
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException("ASSISTANT_INSTRUCTION_USER_INVALID");
    }

    private static String normalizeName(String value) {
        if (value == null) throw new BusinessException("ASSISTANT_INSTRUCTION_NAME_INVALID");
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_NAME_INVALID");
        }
        return normalized;
    }

    private static String normalizeContent(String value) {
        if (value == null) throw new BusinessException("ASSISTANT_INSTRUCTION_CONTENT_INVALID");
        String normalized = value.replace("\r\n", "\n").trim();
        if (normalized.isEmpty() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("ASSISTANT_INSTRUCTION_CONTENT_INVALID");
        }
        return normalized;
    }

    public record ProfileView(Long profileId, String name, boolean enabled, boolean isDefault,
                              Long versionId, Integer version, String content) { }

    public record ResolvedInstruction(Long profileId, Long versionId, Integer version, String content) {
        static ResolvedInstruction platformDefault() { return new ResolvedInstruction(null, null, null, null); }
    }
}
