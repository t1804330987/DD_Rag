package com.dong.ddrag.qa;

import com.dong.ddrag.qa.config.QaChatClientConfiguration;
import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QaChatClientConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, QaChatClientConfiguration.class);

    @Test
    void shouldRegisterQaChatClientBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatClient.class);
            assertThat(context).hasBean("qaRetrievalAdvisor");
            assertThat(context.getBean("qaRetrievalAdvisor")).isInstanceOf(Advisor.class);
            assertThat(context).hasBean("qaSystemPromptTemplate");
            assertThat(context).hasBean("qaUserPromptTemplate");
            assertThat(context).hasBean("qaRagContextPromptTemplate");
            assertThat(context.getBean("qaSystemPromptTemplate")).isInstanceOf(PromptTemplate.class);
        });
    }

    @Configuration
    static class TestConfig {

        @Bean
        ChatModel chatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    return null;
                }
            };
        }

        @Bean
        ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
            return ChatClient.builder(chatModel);
        }

        @Bean
        ReadyChunkDocumentRetriever readyChunkDocumentRetriever() {
            return mock(ReadyChunkDocumentRetriever.class);
        }
    }
}
