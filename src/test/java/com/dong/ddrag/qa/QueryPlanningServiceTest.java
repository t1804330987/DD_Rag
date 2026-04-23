package com.dong.ddrag.qa;

import com.dong.ddrag.qa.model.QueryPlanResult;
import com.dong.ddrag.qa.model.QueryPlanStrategy;
import com.dong.ddrag.qa.service.QueryPlanningService;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryPlanningServiceTest {

    @Test
    void shouldRenderTemplateWithJsonInstructions() {
        PromptTemplate promptTemplate = promptTemplate("""
                输出格式要求：
                - 最终输出必须是一个 JSON 对象
                - 必须包含 `strategy` 字段，取值只能是 `DIRECT`、`REWRITE`、`DECOMPOSE`
                - 必须包含 `queries` 字段，值为字符串数组

                示例：
                strategy=REWRITE, queries=["文档上传流程"]

                用户原始问题：
                {question}
                """);

        String rendered = promptTemplate.render(java.util.Map.of("question", "APIMatch相关内容"));

        assertThat(rendered).contains("APIMatch相关内容");
        assertThat(rendered).contains("strategy=REWRITE");
    }

    @Test
    void shouldReturnDirectPlanFromStructuredOutput() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt(any(Prompt.class))
                .call()
                .entity(QueryPlanResult.class))
                .thenReturn(new QueryPlanResult(QueryPlanStrategy.DIRECT, java.util.List.of("上传流程")));
        QueryPlanningService queryPlanningService = new QueryPlanningService(
                chatClient,
                promptTemplate("原始问题：\n{question}")
        );

        QueryPlanResult result = queryPlanningService.plan("上传流程");

        assertThat(result.strategy()).isEqualTo(QueryPlanStrategy.DIRECT);
        assertThat(result.queries()).containsExactly("上传流程");
    }

    @Test
    void shouldKeepOriginalQuestionWhenRewritePlanReturned() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt(any(Prompt.class))
                .call()
                .entity(QueryPlanResult.class))
                .thenReturn(new QueryPlanResult(QueryPlanStrategy.REWRITE, java.util.List.of("文档上传流程")));
        QueryPlanningService queryPlanningService = new QueryPlanningService(
                chatClient,
                promptTemplate("原始问题：\n{question}")
        );

        QueryPlanResult result = queryPlanningService.plan("请问上传流程是什么");

        assertThat(result.strategy()).isEqualTo(QueryPlanStrategy.REWRITE);
        assertThat(result.queries()).containsExactly("请问上传流程是什么", "文档上传流程");
    }

    @Test
    void shouldReturnDecomposePlanFromStructuredOutput() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt(any(Prompt.class))
                .call()
                .entity(QueryPlanResult.class))
                .thenReturn(new QueryPlanResult(
                        QueryPlanStrategy.DECOMPOSE,
                        java.util.List.of("上传流程是什么", "切片后如何入库")
                ));
        QueryPlanningService queryPlanningService = new QueryPlanningService(
                chatClient,
                promptTemplate("原始问题：\n{question}")
        );

        QueryPlanResult result = queryPlanningService.plan("上传流程是什么，切片后如何入库");

        assertThat(result.strategy()).isEqualTo(QueryPlanStrategy.DECOMPOSE);
        assertThat(result.queries()).containsExactly("上传流程是什么", "切片后如何入库");
    }

    @Test
    void shouldFallbackToOriginalQuestionWhenStructuredOutputInvalid() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt(any(Prompt.class))
                .call()
                .entity(QueryPlanResult.class))
                .thenReturn(new QueryPlanResult(QueryPlanStrategy.DECOMPOSE, java.util.List.of()));
        QueryPlanningService queryPlanningService = new QueryPlanningService(
                chatClient,
                promptTemplate("原始问题：\n{question}")
        );

        QueryPlanResult result = queryPlanningService.plan("上传流程");

        assertThat(result.strategy()).isEqualTo(QueryPlanStrategy.DIRECT);
        assertThat(result.queries()).containsExactly("上传流程");
    }

    @Test
    void shouldFallbackToOriginalQuestionWhenModelCallThrows() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt(any(Prompt.class))
                .call()
                .entity(QueryPlanResult.class))
                .thenThrow(new IllegalStateException("model unavailable"));
        QueryPlanningService queryPlanningService = new QueryPlanningService(
                chatClient,
                promptTemplate("原始问题：\n{question}")
        );

        QueryPlanResult result = queryPlanningService.plan("上传流程");

        assertThat(result.strategy()).isEqualTo(QueryPlanStrategy.DIRECT);
        assertThat(result.queries()).containsExactly("上传流程");
    }

    private PromptTemplate promptTemplate(String template) {
        return PromptTemplate.builder()
                .template(template)
                .build();
    }
}
