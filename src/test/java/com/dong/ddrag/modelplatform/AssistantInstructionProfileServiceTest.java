package com.dong.ddrag.modelplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.AssistantInstructionProfileMapper;
import com.dong.ddrag.modelplatform.mapper.AssistantInstructionProfileVersionMapper;
import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileEntity;
import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileVersionEntity;
import com.dong.ddrag.modelplatform.service.AssistantInstructionProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantInstructionProfileServiceTest {
    @Mock private AssistantInstructionProfileMapper profileMapper;
    @Mock private AssistantInstructionProfileVersionMapper versionMapper;
    @Mock private AssistantSessionMapper sessionMapper;

    @Test
    void shouldCreateFirstImmutableVersionAndMakeDefault() {
        given(profileMapper.insert(any())).willAnswer(invocation -> {
            invocation.<AssistantInstructionProfileEntity>getArgument(0).setId(10L);
            return 1;
        });
        given(versionMapper.insert(any())).willAnswer(invocation -> {
            invocation.<AssistantInstructionProfileVersionEntity>getArgument(0).setId(20L);
            return 1;
        });
        given(profileMapper.updateCurrentVersion(anyLong(), anyLong(), anyLong(), any())).willReturn(1);
        given(profileMapper.setDefault(anyLong(), anyLong(), any())).willReturn(1);

        AssistantInstructionProfileService service = service();
        AssistantInstructionProfileService.ProfileView result = service.create(1L, "技术顾问", " 简洁且严谨 ", true);

        assertThat(result.profileId()).isEqualTo(10L);
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.content()).isEqualTo("简洁且严谨");
        then(profileMapper).should().clearDefaultByUserId(anyLong(), any());
        then(profileMapper).should().updateCurrentVersion(eq(10L), eq(1L), eq(20L), any());
    }

    @Test
    void shouldAppendVersionInsteadOfMutatingHistory() {
        AssistantInstructionProfileEntity profile = profile(10L, 1L, true, false);
        given(profileMapper.selectByIdAndUserId(10L, 1L)).willReturn(profile);
        given(versionMapper.selectMaxVersionByProfileId(10L)).willReturn(2);
        given(versionMapper.insert(any())).willAnswer(invocation -> {
            invocation.<AssistantInstructionProfileVersionEntity>getArgument(0).setId(30L);
            return 1;
        });
        given(profileMapper.updateCurrentVersion(anyLong(), anyLong(), anyLong(), any())).willReturn(1);

        service().updateContent(1L, 10L, "新的策略");

        ArgumentCaptor<AssistantInstructionProfileVersionEntity> version = ArgumentCaptor.forClass(AssistantInstructionProfileVersionEntity.class);
        then(versionMapper).should().insert(version.capture());
        assertThat(version.getValue().getVersion()).isEqualTo(3);
        assertThat(version.getValue().getContent()).isEqualTo("新的策略");
        then(profileMapper).should().updateCurrentVersion(eq(10L), eq(1L), eq(30L), any());
    }

    @Test
    void shouldValidateFullProfileUpdateBeforeChangingItsName() {
        AssistantInstructionProfileService service = service();

        assertThatThrownBy(() -> service.update(1L, 10L, "新名称", " ", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ASSISTANT_INSTRUCTION_CONTENT_INVALID");

        verifyNoInteractions(profileMapper, versionMapper, sessionMapper);
    }

    @Test
    void shouldClearSessionSelectionWhenProfileIsDisabled() {
        given(profileMapper.selectByIdAndUserId(10L, 1L)).willReturn(profile(10L, 1L, true, true));
        given(profileMapper.setEnabled(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean(), any())).willReturn(1);

        service().setEnabled(1L, 10L, false);

        then(sessionMapper).should().clearCurrentInstructionProfile(1L, 10L);
    }

    @Test
    void shouldSoftDeleteProfileAfterClearingSessionSelection() {
        given(profileMapper.selectByIdAndUserId(10L, 1L)).willReturn(profile(10L, 1L, true, true));
        given(profileMapper.softDeleteByIdAndUserId(anyLong(), anyLong(), any())).willReturn(1);

        service().delete(1L, 10L);

        then(sessionMapper).should().clearCurrentInstructionProfile(1L, 10L);
        then(profileMapper).should().softDeleteByIdAndUserId(eq(10L), eq(1L), any());
    }

    @Test
    void shouldNotResolveAnotherUsersProfile() {
        given(profileMapper.selectByIdAndUserId(10L, 2L)).willReturn(null);

        assertThatThrownBy(() -> service().resolveForSession(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ASSISTANT_INSTRUCTION_NOT_FOUND");
    }

    @Test
    void shouldResolveDefaultForNewSession() {
        AssistantInstructionProfileEntity profile = profile(10L, 1L, true, true);
        AssistantInstructionProfileVersionEntity version = version(30L, 10L, 2, "保持简洁");
        given(profileMapper.selectDefaultEnabledByUserId(1L)).willReturn(profile);
        given(versionMapper.selectCurrentByProfileIdAndUserId(10L, 1L)).willReturn(version);

        AssistantInstructionProfileService.ResolvedInstruction resolved = service().resolveDefault(1L);

        assertThat(resolved.profileId()).isEqualTo(10L);
        assertThat(resolved.versionId()).isEqualTo(30L);
        assertThat(resolved.content()).isEqualTo("保持简洁");
    }

    private AssistantInstructionProfileService service() {
        return new AssistantInstructionProfileService(profileMapper, versionMapper, sessionMapper);
    }

    private AssistantInstructionProfileEntity profile(Long id, Long userId, boolean enabled, boolean isDefault) {
        AssistantInstructionProfileEntity profile = new AssistantInstructionProfileEntity();
        profile.setId(id); profile.setUserId(userId); profile.setName("技术顾问");
        profile.setEnabled(enabled); profile.setDefault(isDefault);
        return profile;
    }

    private AssistantInstructionProfileVersionEntity version(Long id, Long profileId, int number, String content) {
        AssistantInstructionProfileVersionEntity version = new AssistantInstructionProfileVersionEntity();
        version.setId(id); version.setProfileId(profileId); version.setVersion(number); version.setContent(content);
        return version;
    }
}
