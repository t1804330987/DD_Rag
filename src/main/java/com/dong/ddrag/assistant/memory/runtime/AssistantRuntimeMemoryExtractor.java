package com.dong.ddrag.assistant.memory.runtime;

import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;

import java.util.List;

public interface AssistantRuntimeMemoryExtractor {

    default List<AssistantRuntimeMemoryChange> extract(Long userId, Long sessionId,
                                                        AssistantRuntimeMemoryState state,
                                                        List<AssistantMessageVO> recentMessages,
                                                        String currentUserMessage) {
        return extract(state, recentMessages, currentUserMessage);
    }

    List<AssistantRuntimeMemoryChange> extract(
            AssistantRuntimeMemoryState state,
            List<AssistantMessageVO> recentMessages,
            String currentUserMessage
    );
}
