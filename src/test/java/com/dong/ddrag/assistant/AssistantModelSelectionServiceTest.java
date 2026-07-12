package com.dong.ddrag.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.dong.ddrag.assistant.service.AssistantModelSelectionService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantModelSelectionServiceTest {
    @Mock private CurrentUserService currentUserService;
    @Mock private ModelConnectionMapper connectionMapper;
    @Mock private ModelConnectionModelMapper modelMapper;
    @Mock private ModelAuthorizationService authorizationService;
    @Mock private HttpServletRequest request;

    @Test
    void shouldExposeOnlyAuthorizedFormalModelsFromPersonalAndPlatformConnections() {
        ModelConnectionEntity own = connection(101L, "USER", "OPENAI", "个人连接");
        ModelConnectionEntity grantedPlatform = connection(102L, "PLATFORM", "DASHSCOPE", "平台连接");
        ModelConnectionEntity deniedPlatform = connection(103L, "PLATFORM", "ANTHROPIC", "未授权连接");
        given(currentUserService.requireBusinessUser(request))
                .willReturn(new CurrentUserService.CurrentUser(7L, "user", "用户"));
        given(connectionMapper.selectByOwner("USER", 7L, "ACTIVE")).willReturn(List.of(own));
        given(connectionMapper.selectByOwner("PLATFORM", null, "ACTIVE"))
                .willReturn(List.of(grantedPlatform, deniedPlatform));
        given(authorizationService.isAuthorized(eq(7L), any(ModelConnectionEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(1) != deniedPlatform);
        given(modelMapper.selectFormalByConnectionId(101L)).willReturn(List.of(model(201L, 101L, "gpt-4.1-mini")));
        given(modelMapper.selectFormalByConnectionId(102L)).willReturn(List.of(model(202L, 102L, "qwen-plus")));

        AssistantModelSelectionService service = new AssistantModelSelectionService(
                currentUserService, connectionMapper, modelMapper, authorizationService);

        assertThat(service.listAvailableModels(request))
                .extracting(AssistantModelSelectionService.AvailableModelView::modelId)
                .containsExactly(202L, 201L);
    }

    private static ModelConnectionEntity connection(Long id, String ownerType, String providerType, String name) {
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setId(id);
        entity.setOwnerType(ownerType);
        entity.setProviderType(providerType);
        entity.setName(name);
        return entity;
    }

    private static ModelConnectionModelEntity model(Long id, Long connectionId, String name) {
        ModelConnectionModelEntity entity = new ModelConnectionModelEntity();
        entity.setId(id);
        entity.setConnectionId(connectionId);
        entity.setModelName(name);
        return entity;
    }
}
