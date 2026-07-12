package com.dong.ddrag.qa.config;

import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class QaChatClientConfiguration {

    @Bean
    public PromptTemplate qaSystemPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/system.st"))
                .build();
    }

    @Bean
    public PromptTemplate qaUserPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/user.st"))
                .build();
    }

    @Bean
    public PromptTemplate qaRagContextPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/rag-context.st"))
                .build();
    }

    @Bean("qaRetrievalAdvisor")
    public RetrievalAugmentationAdvisor qaRetrievalAdvisor(
            ReadyChunkDocumentRetriever readyChunkDocumentRetriever,
            @Qualifier("qaRagContextPromptTemplate") PromptTemplate qaRagContextPromptTemplate
    ) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(readyChunkDocumentRetriever)
                .queryAugmenter(new ContextualQueryAugmenter.Builder()
                        .allowEmptyContext(true)
                        .promptTemplate(qaRagContextPromptTemplate)
                        .build())
                .build();
    }
}
