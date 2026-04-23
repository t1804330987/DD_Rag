package com.dong.ddrag.qa;

import com.dong.ddrag.qa.model.KnowledgeAnswerOutput;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import com.dong.ddrag.qa.rag.RetrievedEvidenceBundle;
import com.dong.ddrag.qa.service.QaChatService;
import com.dong.ddrag.qa.support.CitationAssembler;
import com.dong.ddrag.qa.support.QaAnswerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QaChatServiceTest {

    @Test
    void shouldReturnUnansweredWithoutContext() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ReadyChunkDocumentRetriever retriever = mock(ReadyChunkDocumentRetriever.class);
        when(retriever.retrieveEvidence(2001L, "上传流程")).thenReturn(RetrievedEvidenceBundle.empty());
        QaChatService qaChatService = new QaChatService(
                chatClient,
                promptTemplate("问题：\n{question}\n证据等级：\n{evidenceLevel}\n回答策略：\n{evidenceGuidance}"),
                retriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler()
        );

        AskQuestionResponse response = qaChatService.ask(2001L, "上传流程");

        assertThat(response.answered()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("INSUFFICIENT_EVIDENCE");
        verifyNoInteractions(chatClient);
    }

    @Test
    void shouldReturnAnsweredResponseWhenChatClientReturnsValidJson() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ReadyChunkDocumentRetriever retriever = mock(ReadyChunkDocumentRetriever.class);
        when(retriever.retrieveEvidence(2001L, "上传流程")).thenReturn(bundle(
                EvidenceLevel.SUFFICIENT,
                "当前证据较充分，可以正常回答，但仍然不得超出证据进行臆测。",
                List.of(
                        Document.builder()
                                .id("E1")
                                .text("产品团队每两周发布一次。")
                                .metadata(Map.of(
                                        "evidenceId", "E1",
                                        "documentId", 1001L,
                                        "chunkId", 9001L,
                                        "chunkIndex", 0,
                                        "fileName", "产品手册.md",
                                        "score", 0.91D
                                ))
                                .build()
                )
        ));
        when(chatClient.prompt(any(Prompt.class))
                .advisors(any(java.util.function.Consumer.class))
                .call()
                .entity(KnowledgeAnswerOutput.class)).thenReturn(
                        new KnowledgeAnswerOutput(
                                true,
                                "产品团队每两周发布一次。",
                                null,
                                null
                        )
                );
        QaChatService qaChatService = new QaChatService(
                chatClient,
                promptTemplate("问题：\n{question}\n证据等级：\n{evidenceLevel}\n回答策略：\n{evidenceGuidance}"),
                retriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler()
        );

        AskQuestionResponse response = qaChatService.ask(2001L, "上传流程");

        assertThat(response.answered()).isTrue();
        assertThat(response.answer()).isEqualTo("产品团队每两周发布一次。");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().fileName()).isEqualTo("产品手册.md");
    }

    @Test
    void shouldFallbackToRawContentWhenStructuredOutputFails() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(CallResponseSpec.class);
        ReadyChunkDocumentRetriever retriever = mock(ReadyChunkDocumentRetriever.class);
        when(retriever.retrieveEvidence(2001L, "上传流程")).thenReturn(bundle(
                EvidenceLevel.PARTIAL,
                "当前证据只覆盖部分问题，只能回答证据明确支持的部分，未覆盖部分必须明确说明不足。",
                List.of(
                        Document.builder()
                                .id("E1")
                                .text("产品团队每两周发布一次。")
                                .metadata(Map.of(
                                        "evidenceId", "E1",
                                        "documentId", 1001L,
                                        "chunkId", 9001L,
                                        "chunkIndex", 0,
                                        "fileName", "产品手册.md",
                                        "score", 0.91D
                                ))
                                .build()
                )
        ));
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(KnowledgeAnswerOutput.class))
                .thenThrow(new IllegalStateException("structured output failed"));
        when(callResponseSpec.content()).thenReturn("""
                {
                  "answered": true,
                  "answer": "产品团队每两周发布一次。"
                }
                """);
        QaChatService qaChatService = new QaChatService(
                chatClient,
                promptTemplate("问题：\n{question}\n证据等级：\n{evidenceLevel}\n回答策略：\n{evidenceGuidance}"),
                retriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler()
        );

        AskQuestionResponse response = qaChatService.ask(2001L, "上传流程");

        assertThat(response.answered()).isTrue();
        assertThat(response.answer()).isEqualTo("产品团队每两周发布一次。");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().fileName()).isEqualTo("产品手册.md");
    }

    @Test
    void shouldDeduplicateCitationsByFileName() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ReadyChunkDocumentRetriever retriever = mock(ReadyChunkDocumentRetriever.class);
        when(retriever.retrieveEvidence(2001L, "上传流程")).thenReturn(bundle(
                EvidenceLevel.SUFFICIENT,
                "当前证据较充分，可以正常回答，但仍然不得超出证据进行臆测。",
                List.of(
                        Document.builder()
                                .id("E1")
                                .text("产品团队每两周发布一次。")
                                .metadata(Map.of(
                                        "evidenceId", "E1",
                                        "documentId", 1001L,
                                        "chunkId", 9001L,
                                        "chunkIndex", 0,
                                        "fileName", "产品手册.md",
                                        "score", 0.91D
                                ))
                                .build(),
                        Document.builder()
                                .id("E2")
                                .text("产品团队每个迭代结束后复盘。")
                                .metadata(Map.of(
                                        "evidenceId", "E2",
                                        "documentId", 1001L,
                                        "chunkId", 9002L,
                                        "chunkIndex", 1,
                                        "fileName", "产品手册.md",
                                        "score", 0.88D
                                ))
                                .build()
                )
        ));
        when(chatClient.prompt(any(Prompt.class))
                .advisors(any(java.util.function.Consumer.class))
                .call()
                .entity(KnowledgeAnswerOutput.class)).thenReturn(
                        new KnowledgeAnswerOutput(
                                true,
                                "产品团队每两周发布一次。",
                                null,
                                null
                        )
                );
        QaChatService qaChatService = new QaChatService(
                chatClient,
                promptTemplate("问题：\n{question}\n证据等级：\n{evidenceLevel}\n回答策略：\n{evidenceGuidance}"),
                retriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler()
        );

        AskQuestionResponse response = qaChatService.ask(2001L, "上传流程");

        assertThat(response.answered()).isTrue();
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().fileName()).isEqualTo("产品手册.md");
    }

    @Test
    void shouldRenderUserPromptWithEvidenceLevelConstraints() {
        ReadyChunkDocumentRetriever retriever = mock(ReadyChunkDocumentRetriever.class);
        QaChatService qaChatService = new QaChatService(
                mock(ChatClient.class),
                promptTemplate("""
                        问题：
                        {question}

                        证据等级：
                        {evidenceLevel}

                        回答策略：
                        {evidenceGuidance}
                        """),
                retriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler()
        );
        RetrievedEvidenceBundle evidenceBundle = bundle(
                EvidenceLevel.WEAK,
                "当前证据相关性有限，只能谨慎回答，必须明确说明依据有限，不能给出确定性结论。",
                List.of(
                        Document.builder()
                                .id("E1")
                                .text("产品团队每两周发布一次。")
                                .metadata(Map.of(
                                        "evidenceId", "E1",
                                        "documentId", 1001L,
                                        "chunkId", 9001L,
                                        "chunkIndex", 0,
                                        "fileName", "产品手册.md",
                                        "score", 0.91D
                                ))
                                .build()
                )
        );

        Prompt renderedPrompt = invokeCreateUserPrompt(qaChatService, "上传流程", evidenceBundle);

        String renderedText = renderedPrompt.getInstructions().stream()
                .map(message -> message.getText())
                .reduce("", String::concat);
        assertThat(renderedText).contains("WEAK");
        assertThat(renderedText).contains("只能谨慎回答");
    }

    private RetrievedEvidenceBundle bundle(
            EvidenceLevel evidenceLevel,
            String evidenceGuidance,
            List<Document> documents
    ) {
        return new RetrievedEvidenceBundle(documents, evidenceLevel, evidenceGuidance);
    }

    private Prompt invokeCreateUserPrompt(
            QaChatService qaChatService,
            String question,
            RetrievedEvidenceBundle evidenceBundle
    ) {
        try {
            Method method = QaChatService.class.getDeclaredMethod(
                    "createUserPrompt",
                    String.class,
                    RetrievedEvidenceBundle.class
            );
            method.setAccessible(true);
            return (Prompt) method.invoke(qaChatService, question, evidenceBundle);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法调用 createUserPrompt", exception);
        }
    }

    private PromptTemplate promptTemplate(String template) {
        return PromptTemplate.builder()
                .template(template)
                .build();
    }
}
