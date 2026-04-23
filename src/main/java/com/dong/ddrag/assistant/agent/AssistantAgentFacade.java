package com.dong.ddrag.assistant.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.support.config.AssistantPromptContextBuilder;
import com.dong.ddrag.assistant.support.config.AssistantRunnableConfigFactory;
import com.dong.ddrag.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Component
public class AssistantAgentFacade {

    private static final Logger log = LoggerFactory.getLogger(AssistantAgentFacade.class);

    private final AssistantReactAgentFactory assistantReactAgentFactory;
    private final AssistantRunnableConfigFactory assistantRunnableConfigFactory;
    private final AssistantPromptContextBuilder assistantPromptContextBuilder;

    public AssistantAgentFacade(
            AssistantReactAgentFactory assistantReactAgentFactory,
            AssistantRunnableConfigFactory assistantRunnableConfigFactory,
            AssistantPromptContextBuilder assistantPromptContextBuilder
    ) {
        this.assistantReactAgentFactory = assistantReactAgentFactory;
        this.assistantRunnableConfigFactory = assistantRunnableConfigFactory;
        this.assistantPromptContextBuilder = assistantPromptContextBuilder;
    }

    public AssistantAgentResult chat(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String userMessage
    ) {
        // 这里把“仅对话模式”的运行时输入收口成两部分：
        // 1) instruction：系统提示词
        // 2) runnableConfig：session/user/toolMode 等 metadata，供 hooks 在 BEFORE_MODEL 阶段读取
        String instruction = assistantPromptContextBuilder.buildChatInstruction(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        RunnableConfig runnableConfig = assistantRunnableConfigFactory.create(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        // 当前虽然是“仅对话模式”，底层仍然使用 ReactAgent，只是 system prompt 已改成纯对话风格。
        AssistantKnowledgeBaseToolResultHolder resultHolder = new AssistantKnowledgeBaseToolResultHolder();
        ReactAgent agent = assistantReactAgentFactory.createAgent(instruction, toolMode, groupId, resultHolder);
        AssistantMessage assistantMessage;
        try {
            assistantMessage = agent.call(userMessage, runnableConfig);
        } catch (GraphRunnerException exception) {
            throw new BusinessException("助手调用失败", exception);
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "AssistantAgentFacade.chat result. userId={}, sessionId={}, toolMode={}, groupId={}, text={}, hasToolCalls={}, toolCallCount={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    assistantMessage == null ? null : abbreviate(assistantMessage.getText()),
                    assistantMessage != null && assistantMessage.hasToolCalls(),
                    assistantMessage == null || assistantMessage.getToolCalls() == null ? 0 : assistantMessage.getToolCalls().size()
            );
        }
        if (assistantMessage == null || assistantMessage.getText() == null || assistantMessage.getText().isBlank()) {
            throw new BusinessException("助手返回内容为空");
        }
        return new AssistantAgentResult(assistantMessage.getText(), resultHolder.currentCitations());
    }

    public AssistantAgentResult streamChat(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String userMessage,
            Consumer<String> deltaConsumer
    ) {
        String instruction = assistantPromptContextBuilder.buildChatInstruction(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        RunnableConfig runnableConfig = assistantRunnableConfigFactory.create(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        AssistantKnowledgeBaseToolResultHolder resultHolder = new AssistantKnowledgeBaseToolResultHolder();
        ReactAgent agent = assistantReactAgentFactory.createAgent(instruction, toolMode, groupId, resultHolder);
        StringBuilder finalReply = new StringBuilder();
        try {
            // stream() 返回的是图执行过程中的节点输出，这里只抽取模型实际产出的文本 delta。
            Flux<NodeOutput> stream = agent.stream(userMessage, runnableConfig);
            stream.doOnNext(output -> handleStreamingOutput(output, deltaConsumer, finalReply))
                    .blockLast();
        } catch (GraphRunnerException exception) {
            throw new BusinessException("助手调用失败", exception);
        }
        String reply = finalReply.toString().trim();
        if (log.isDebugEnabled()) {
            log.debug(
                    "AssistantAgentFacade.streamChat result. userId={}, sessionId={}, toolMode={}, groupId={}, reply={}, citationCount={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    abbreviate(reply),
                    resultHolder.currentCitations().size()
            );
        }
        if (reply.isBlank()) {
            throw new BusinessException("助手返回内容为空");
        }
        return new AssistantAgentResult(reply, resultHolder.currentCitations());
    }

    private void handleStreamingOutput(
            NodeOutput output,
            Consumer<String> deltaConsumer,
            StringBuilder finalReply
    ) {
        if (!(output instanceof StreamingOutput streamingOutput)) {
            return;
        }
        OutputType type = streamingOutput.getOutputType();
        Message message = streamingOutput.message();
        if (!(message instanceof AssistantMessage assistantMessage)) {
            return;
        }
        if (type == OutputType.AGENT_MODEL_STREAMING) {
            // 正常流式路径下，模型边生成边向前端透传文本。
            String delta = normalizeStreamingDelta(assistantMessage.getText(), finalReply.toString());
            if (delta.isBlank()) {
                return;
            }
            finalReply.append(delta);
            deltaConsumer.accept(delta);
            return;
        }
        if (type == OutputType.AGENT_MODEL_FINISHED
                && !assistantMessage.hasToolCalls()
                && finalReply.isEmpty()) {
            // 某些情况下模型不会走 streaming delta，而是一次性在 finished 节点返回完整文本，这里兜底补发。
            String fullText = assistantMessage.getText();
            if (fullText == null || fullText.isBlank()) {
                return;
            }
            finalReply.append(fullText);
            deltaConsumer.accept(fullText);
        }
    }

    private String normalizeStreamingDelta(String candidateText, String accumulatedReply) {
        if (candidateText == null || candidateText.isBlank()) {
            return "";
        }
        if (accumulatedReply.isEmpty()) {
            return candidateText;
        }
        // Spring AI Alibaba 的流式节点在某些模型下会反复返回“截至当前的全文”，
        // 这里需要把累计前缀裁掉，转换成真正的增量 delta，再交给前端做 append。
        if (candidateText.startsWith(accumulatedReply)) {
            return candidateText.substring(accumulatedReply.length());
        }
        return candidateText;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace('\n', ' ').trim();
        if (normalized.length() > 200) {
            return normalized.substring(0, 200) + "...";
        }
        return normalized;
    }
}
