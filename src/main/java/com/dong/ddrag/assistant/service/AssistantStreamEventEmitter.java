package com.dong.ddrag.assistant.service;

import com.dong.ddrag.assistant.model.vo.chat.AssistantChatStreamEvent;

public interface AssistantStreamEventEmitter {

    void emit(AssistantChatStreamEvent event);
}
