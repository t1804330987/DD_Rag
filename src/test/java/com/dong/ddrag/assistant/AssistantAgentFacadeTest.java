package com.dong.ddrag.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
import com.dong.ddrag.assistant.agent.AssistantReactAgentFactory;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.support.config.AssistantPromptContextBuilder;
import com.dong.ddrag.assistant.support.config.AssistantRunnableConfigFactory;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;
import com.dong.ddrag.modelplatform.service.AssistantInstructionProfileService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AssistantAgentFacadeTest {
    @Mock private AssistantReactAgentFactory factory;
    @Mock private ReactAgent agent;
    @Mock private ModelRuntimeService runtimeService;
    @Mock private ModelInvocationDispatcher dispatcher;
    @Mock private AssistantInstructionProfileService instructionProfiles;

    @Test
    void shouldUseGovernedModelAndInstructionSnapshot() throws Exception {
        AssistantAgentFacade facade = facade();
        given(factory.createAgent(any(ChatModel.class), any(), eq(AssistantToolMode.CHAT), eq(1001L), eq(null), any()))
                .willReturn(agent);
        given(agent.call(eq("请介绍一下自己"), any(RunnableConfig.class)))
                .willReturn(new AssistantMessage("你好，我是个人智能助手。"));

        AssistantAgentResult response = facade.chat(1001L, 2001L, AssistantToolMode.CHAT, null, "请介绍一下自己");

        assertThat(response.reply()).isEqualTo("你好，我是个人智能助手。");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        then(factory).should().createAgent(any(ChatModel.class), prompt.capture(), eq(AssistantToolMode.CHAT), eq(1001L), eq(null), any());
        assertThat(prompt.getValue()).contains("个人助手指令").contains("保持简洁")
                .contains("当前会话ID：2001");
        then(runtimeService).should().resolveAssistant(eq(1001L), eq(2001L), any());
        then(instructionProfiles).should().resolveCurrentForSession(1001L, 2001L);
    }

    @Test
    void shouldAllowKnowledgeBaseSearchModeForAgentToolExecution() throws Exception {
        AssistantAgentFacade facade = facade();
        given(factory.createAgent(any(ChatModel.class), any(), eq(AssistantToolMode.KB_SEARCH), eq(1001L), eq(3001L), any()))
                .willReturn(agent);
        given(agent.call(eq("测试"), any(RunnableConfig.class))).willReturn(new AssistantMessage("知识库回答"));

        assertThat(facade.chat(1001L, 2001L, AssistantToolMode.KB_SEARCH, 3001L, "测试").reply())
                .isEqualTo("知识库回答");
    }

    @Test
    void shouldNormalizeAccumulatedStreamingTextToIncrementalDelta() throws Exception {
        AssistantAgentFacade facade = facade();
        given(factory.createAgent(any(ChatModel.class), any(), eq(AssistantToolMode.CHAT), eq(1001L), eq(null), any()))
                .willReturn(agent);
        NodeOutput first = streamingOutput(OutputType.AGENT_MODEL_STREAMING, "你好");
        NodeOutput second = streamingOutput(OutputType.AGENT_MODEL_STREAMING, "你好，我是微明");
        NodeOutput third = streamingOutput(OutputType.AGENT_MODEL_STREAMING, "你好，我是微明。很高兴认识你");
        given(agent.stream(eq("你好"), any(RunnableConfig.class))).willReturn(Flux.just(first, second, third));
        List<String> deltas = new ArrayList<>();

        AssistantAgentResult reply = facade.streamChat(1001L, 2001L, AssistantToolMode.CHAT, null, "你好", deltas::add);

        assertThat(deltas).containsExactly("你好", "，我是微明", "。很高兴认识你");
        assertThat(reply.reply()).isEqualTo("你好，我是微明。很高兴认识你");
    }

    private AssistantAgentFacade facade() {
        given(runtimeService.resolveAssistant(any(), any(), any())).willReturn(modelContext());
        given(instructionProfiles.resolveCurrentForSession(any(), any()))
                .willReturn(new AssistantInstructionProfileService.ResolvedInstruction(7L, 8L, 2, "保持简洁"));
        return new AssistantAgentFacade(factory, new AssistantRunnableConfigFactory(), new AssistantPromptContextBuilder(),
                runtimeService, dispatcher, instructionProfiles);
    }

    private ModelInvocationContext modelContext() {
        return new ModelInvocationContext(1001L, ModelScenario.ASSISTANT_CHAT, 10L, 20L, 1L,
                ProviderType.DASHSCOPE, "qwen", "shared", ConnectionOwnerType.PLATFORM, 2001L,
                null, null, "turn", null);
    }

    private NodeOutput streamingOutput(OutputType type, String text) {
        StreamingOutput output = mock(StreamingOutput.class);
        given(output.getOutputType()).willReturn(type);
        given(output.message()).willReturn(new AssistantMessage(text));
        return output;
    }
}
