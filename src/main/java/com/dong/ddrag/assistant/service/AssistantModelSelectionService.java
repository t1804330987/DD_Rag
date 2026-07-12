package com.dong.ddrag.assistant.service;

import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.service.ModelAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Exposes only models that can be selected for a business user's Assistant session. */
@Service
public class AssistantModelSelectionService {
    private static final String ACTIVE = "ACTIVE";

    private final CurrentUserService currentUserService;
    private final ModelConnectionMapper connectionMapper;
    private final ModelConnectionModelMapper modelMapper;
    private final ModelAuthorizationService authorizationService;

    public AssistantModelSelectionService(CurrentUserService currentUserService,
                                          ModelConnectionMapper connectionMapper,
                                          ModelConnectionModelMapper modelMapper,
                                          ModelAuthorizationService authorizationService) {
        this.currentUserService = currentUserService;
        this.connectionMapper = connectionMapper;
        this.modelMapper = modelMapper;
        this.authorizationService = authorizationService;
    }

    public List<AvailableModelView> listAvailableModels(HttpServletRequest request) {
        Long userId = currentUserService.requireBusinessUser(request).userId();
        List<ModelConnectionEntity> candidates = new ArrayList<>();
        candidates.addAll(connectionMapper.selectByOwner("USER", userId, ACTIVE));
        candidates.addAll(connectionMapper.selectByOwner("PLATFORM", null, ACTIVE));
        return candidates.stream()
                .filter(connection -> authorizationService.isAuthorized(userId, connection))
                .flatMap(connection -> modelMapper.selectFormalByConnectionId(connection.getId()).stream()
                        .map(model -> new AvailableModelView(connection.getId(), model.getId(),
                                connection.getProviderType(), connection.getName(), model.getModelName(),
                                connection.getOwnerType())))
                .sorted(Comparator.comparing(AvailableModelView::ownerType)
                        .thenComparing(AvailableModelView::providerType)
                        .thenComparing(AvailableModelView::modelName))
                .toList();
    }

    public record AvailableModelView(Long connectionId, Long modelId, String providerType,
                                     String connectionName, String modelName, String ownerType) { }
}
