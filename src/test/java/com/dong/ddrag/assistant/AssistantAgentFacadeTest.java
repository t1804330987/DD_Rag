package com.dong.ddrag.assistant;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
import com.dong.ddrag.assistant.agent.AssistantReactAgentFactory;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.support.config.AssistantPromptContextBuilder;
import com.dong.ddrag.assistant.support.config.AssistantRunnableConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AssistantAgentFacadeTest {

    @Mock
    private AssistantReactAgentFactory assistantReactAgentFactory;

    @Mock
    private ReactAgent reactAgent;

    @Test
    void shouldBuildRunnableConfigAndCallChatAgent() throws Exception {
        AssistantRunnableConfigFactory runnableConfigFactory = new AssistantRunnableConfigFactory();
        AssistantPromptContextBuilder promptContextBuilder = new AssistantPromptContextBuilder();
        AssistantAgentFacade facade = new AssistantAgentFacade(
                assistantReactAgentFactory,
                runnableConfigFactory,
                promptContextBuilder
        );
        given(assistantReactAgentFactory.createAgent(any(), eq(AssistantToolMode.CHAT), eq(null), any()))
                .willReturn(reactAgent);
        given(reactAgent.call(eq("请介绍一下自己"), any(RunnableConfig.class)))
                .willReturn(new AssistantMessage("你好，我是个人智能助手。"));

        AssistantAgentResult response = facade.chat(1001L, 2001L, AssistantToolMode.CHAT, null, "请介绍一下自己");

        assertThat(response.reply()).isEqualTo("你好，我是个人智能助手。");
        assertThat(response.citations()).isEmpty();
        ArgumentCaptor<String> instructionCaptor = ArgumentCaptor.forClass(String.class);
        then(assistantReactAgentFactory).should().createAgent(
                instructionCaptor.capture(),
                eq(AssistantToolMode.CHAT),
                eq(null),
                any()
        );
        assertThat(instructionCaptor.getValue()).contains("你是一个可靠、克制、清晰的中文智能助手");
        assertThat(instructionCaptor.getValue()).contains("当前会话ID：2001");
        assertThat(instructionCaptor.getValue()).contains("当前模式：CHAT");

        ArgumentCaptor<RunnableConfig> configCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        then(reactAgent).should().call(eq("请介绍一下自己"), configCaptor.capture());
        RunnableConfig runnableConfig = configCaptor.getValue();
        assertThat(runnableConfig.threadId()).contains("user:1001:session:2001");
        assertThat(runnableConfig.metadata("userId")).contains(1001L);
        assertThat(runnableConfig.metadata("sessionId")).contains(2001L);
        assertThat(runnableConfig.metadata("toolMode")).contains("CHAT");
        assertThat(runnableConfig.metadata("groupId")).isEmpty();
    }

    @Test
    void shouldAllowKnowledgeBaseSearchModeForAgentToolExecution() throws Exception {
        AssistantAgentFacade facade = new AssistantAgentFacade(
                assistantReactAgentFactory,
                new AssistantRunnableConfigFactory(),
                new AssistantPromptContextBuilder()
        );
        given(assistantReactAgentFactory.createAgent(any(), eq(AssistantToolMode.KB_SEARCH), eq(3001L), any()))
                .willReturn(reactAgent);
        given(reactAgent.call(eq("测试"), any(RunnableConfig.class)))
                .willReturn(new AssistantMessage("知识库回答"));

        AssistantAgentResult result = facade.chat(1001L, 2001L, AssistantToolMode.KB_SEARCH, 3001L, "测试");

        assertThat(result.reply()).isEqualTo("知识库回答");
        then(assistantReactAgentFactory).should().createAgent(any(), eq(AssistantToolMode.KB_SEARCH), eq(3001L), any());
    }

    @Test
    void shouldBuildRunnableConfigWithExpectedMetadata() {
        RunnableConfig runnableConfig = new AssistantRunnableConfigFactory()
                .create(1001L, 2001L, AssistantToolMode.CHAT, null);

        assertThat(runnableConfig.threadId()).contains("user:1001:session:2001");
        assertThat(runnableConfig.metadata("userId")).contains(1001L);
        assertThat(runnableConfig.metadata("sessionId")).contains(2001L);
        assertThat(runnableConfig.metadata("toolMode")).contains("CHAT");
        assertThat(runnableConfig.metadata("groupId")).isEmpty();
    }

    @Test
    void shouldNormalizeAccumulatedStreamingTextToIncrementalDelta() throws Exception {
        AssistantRunnableConfigFactory runnableConfigFactory = new AssistantRunnableConfigFactory();
        AssistantPromptContextBuilder promptContextBuilder = new AssistantPromptContextBuilder();
        AssistantAgentFacade facade = new AssistantAgentFacade(
                assistantReactAgentFactory,
                runnableConfigFactory,
                promptContextBuilder
        );
        given(assistantReactAgentFactory.createAgent(any(), eq(AssistantToolMode.CHAT), eq(null), any()))
                .willReturn(reactAgent);
        NodeOutput firstOutput = streamingOutput(OutputType.AGENT_MODEL_STREAMING, "你好");
        NodeOutput secondOutput = streamingOutput(OutputType.AGENT_MODEL_STREAMING, "你好，我是微明");
        NodeOutput thirdOutput = streamingOutput(OutputType.AGENT_MODEL_STREAMING, "你好，我是微明。很高兴认识你");
        given(reactAgent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(
                        firstOutput,
                        secondOutput,
                        thirdOutput
                ));
        List<String> deltas = new ArrayList<>();

        AssistantAgentResult reply = facade.streamChat(
                1001L,
                2001L,
                AssistantToolMode.CHAT,
                null,
                "你好",
                deltas::add
        );

        assertThat(deltas).containsExactly("你好", "，我是微明", "。很高兴认识你");
        assertThat(reply.reply()).isEqualTo("你好，我是微明。很高兴认识你");
        assertThat(reply.citations()).isEmpty();
    }

    private NodeOutput streamingOutput(OutputType outputType, String text) {
        StreamingOutput output = mock(StreamingOutput.class);
        given(output.getOutputType()).willReturn(outputType);
        given(output.message()).willReturn(new AssistantMessage(text));
        return output;
    }
}
