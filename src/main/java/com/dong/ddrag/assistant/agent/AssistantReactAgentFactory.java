package com.dong.ddrag.assistant.agent;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.dong.ddrag.assistant.memory.AssistantShortTermMemoryHook;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AssistantReactAgentFactory {

    // KB_SEARCH 至少需要经历“模型决定调工具 -> 工具执行 -> 模型基于工具结果生成最终回答”，
    // 递归上限过小会导致图在最终回答前被截断，返回空 AssistantMessage。
    private static final int AGENT_RECURSION_LIMIT = 10;

    private final ChatModel chatModel;
    private final AssistantShortTermMemoryHook assistantShortTermMemoryHook;
    private final AssistantKnowledgeBaseTool assistantKnowledgeBaseTool;

    public AssistantReactAgentFactory(
            ChatModel chatModel,
            AssistantShortTermMemoryHook assistantShortTermMemoryHook,
            AssistantKnowledgeBaseTool assistantKnowledgeBaseTool
    ) {
        this.chatModel = chatModel;
        this.assistantShortTermMemoryHook = assistantShortTermMemoryHook;
        this.assistantKnowledgeBaseTool = assistantKnowledgeBaseTool;
    }

    public ReactAgent createAgent(
            String instruction,
            AssistantToolMode toolMode,
            Long groupId,
            AssistantKnowledgeBaseToolResultHolder resultHolder
    ) {
        // 当前“仅对话模式”仍复用 ReactAgent 运行时：
        // system prompt 与短期上下文保留，长期记忆工具已整体移除。
        com.alibaba.cloud.ai.graph.agent.Builder builder = ReactAgent.builder()
                .name("assistant_chat_agent")
                .model(chatModel)
                .instruction(instruction)
                .hooks(assistantShortTermMemoryHook)
                .compileConfig(CompileConfig.builder()
                        .recursionLimit(AGENT_RECURSION_LIMIT)
                        .build())
                // MemorySaver 只负责图运行态的 checkpoint，不是正式业务记忆的事实源。
                .saver(new MemorySaver());
        if (toolMode == AssistantToolMode.KB_SEARCH) {
            builder.methodTools(assistantKnowledgeBaseTool)
                    .toolContext(Map.of(
                            AssistantKnowledgeBaseTool.GROUP_ID_CONTEXT_KEY, groupId,
                            AssistantKnowledgeBaseTool.RESULT_HOLDER_CONTEXT_KEY, resultHolder
                    ));
        }
        return builder.build();
    }
}
