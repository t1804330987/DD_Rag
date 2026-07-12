package com.dong.ddrag.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dong.ddrag.qa.config.QaChatClientConfiguration;
import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class QaPromptConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, QaChatClientConfiguration.class);

    @Test
    void shouldRegisterQaPromptAndRetrievalAdvisorBeansWithoutGlobalChatClient() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(org.springframework.ai.chat.client.ChatClient.class);
            assertThat(context).hasBean("qaRetrievalAdvisor");
            assertThat(context.getBean("qaRetrievalAdvisor")).isInstanceOf(Advisor.class);
            assertThat(context.getBean("qaSystemPromptTemplate")).isInstanceOf(PromptTemplate.class);
            assertThat(context.getBean("qaUserPromptTemplate")).isInstanceOf(PromptTemplate.class);
            assertThat(context.getBean("qaRagContextPromptTemplate")).isInstanceOf(PromptTemplate.class);
        });
    }

    @Configuration
    static class TestConfig {
        @Bean
        ReadyChunkDocumentRetriever readyChunkDocumentRetriever() {
            return mock(ReadyChunkDocumentRetriever.class);
        }
    }
}
