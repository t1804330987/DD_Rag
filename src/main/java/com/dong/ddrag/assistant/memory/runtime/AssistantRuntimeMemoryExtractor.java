package com.dong.ddrag.assistant.memory.runtime;

import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;

import java.util.List;

public interface AssistantRuntimeMemoryExtractor {

    List<AssistantRuntimeMemoryChange> extract(
            AssistantRuntimeMemoryState state,
            List<AssistantMessageVO> recentMessages,
            String currentUserMessage
    );
}
