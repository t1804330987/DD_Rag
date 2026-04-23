package com.dong.ddrag.assistant.memory;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class AssistantMemoryPromptConfiguration {

    @Bean
    @Qualifier("assistantSessionMemoryPromptTemplate")
    public PromptTemplate assistantSessionMemoryPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/assistant/session-memory-update.st"))
                .build();
    }

    @Bean
    @Qualifier("assistantCompactSummaryPromptTemplate")
    public PromptTemplate assistantCompactSummaryPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/assistant/session-compact-summary.st"))
                .build();
    }

    @Bean
    @Qualifier("assistantRuntimeCompactPromptTemplate")
    public PromptTemplate assistantRuntimeCompactPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/assistant/runtime-compact-summary.st"))
                .build();
    }
}
