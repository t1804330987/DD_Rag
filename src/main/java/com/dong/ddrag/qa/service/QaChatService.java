package com.dong.ddrag.qa.service;

import com.dong.ddrag.qa.model.KnowledgeAnswerOutput;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import com.dong.ddrag.qa.rag.EvidenceRetriever;
import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import com.dong.ddrag.qa.rag.RetrievedEvidenceBundle;
import com.dong.ddrag.qa.support.CitationAssembler;
import com.dong.ddrag.qa.support.QaAnswerParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.runtime.GovernedChatModel;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QaChatService {

    private static final Logger log = LoggerFactory.getLogger(QaChatService.class);
    private static final String INSUFFICIENT_CODE = "INSUFFICIENT_EVIDENCE";
    private static final String INSUFFICIENT_MESSAGE = "检索到的有效证据不足，暂不回答。";
    private static final String FORMAT_ERROR_CODE = "ANSWER_FORMAT_ERROR";
    private static final String FORMAT_ERROR_MESSAGE = "模型返回格式错误，无法解析回答。";

    private final PromptTemplate qaSystemPromptTemplate;
    private final RetrievalAugmentationAdvisor qaRetrievalAdvisor;
    private final PromptTemplate qaUserPromptTemplate;
    private final EvidenceRetriever evidenceRetriever;
    private final QaAnswerParser answerParser;
    private final CitationAssembler citationAssembler;
    private final ModelRuntimeService modelRuntimeService;
    private final ModelInvocationDispatcher invocationDispatcher;

    public QaChatService(
            @Qualifier("qaSystemPromptTemplate") PromptTemplate qaSystemPromptTemplate,
            @Qualifier("qaRetrievalAdvisor") RetrievalAugmentationAdvisor qaRetrievalAdvisor,
            @Qualifier("qaUserPromptTemplate") PromptTemplate qaUserPromptTemplate,
            EvidenceRetriever evidenceRetriever,
            QaAnswerParser answerParser,
            CitationAssembler citationAssembler,
            ModelRuntimeService modelRuntimeService,
            ModelInvocationDispatcher invocationDispatcher
    ) {
        this.qaSystemPromptTemplate = qaSystemPromptTemplate;
        this.qaRetrievalAdvisor = qaRetrievalAdvisor;
        this.qaUserPromptTemplate = qaUserPromptTemplate;
        this.evidenceRetriever = evidenceRetriever;
        this.answerParser = answerParser;
        this.citationAssembler = citationAssembler;
        this.modelRuntimeService = modelRuntimeService;
        this.invocationDispatcher = invocationDispatcher;
    }

    public AskQuestionResponse ask(Long userId, Long groupId, String question) {
        RetrievedEvidenceBundle evidenceBundle = evidenceRetriever.retrieve(userId, groupId, question);
        List<Document> documents = evidenceBundle.documents();
        if (documents.isEmpty()) {
            return AskQuestionResponse.unanswered(INSUFFICIENT_CODE, INSUFFICIENT_MESSAGE, List.of());
        }
        KnowledgeAnswerOutput output = getStructuredAnswer(userId, groupId, question, evidenceBundle);
        if (output == null) {
            return AskQuestionResponse.unanswered(FORMAT_ERROR_CODE, FORMAT_ERROR_MESSAGE, List.of());
        }
        if (!output.answered() || !StringUtils.hasText(output.answer())) {
            return AskQuestionResponse.unanswered(output.reasonCode(), output.reasonMessage(), List.of());
        }
        return AskQuestionResponse.answered(
                output.answer().trim(),
                citationAssembler.assembleDocuments(documents)
        );
    }

    public AskQuestionResponse ask(Long groupId, String question) {
        throw new IllegalStateException("QA 调用必须提供实际用户上下文");
    }

    private KnowledgeAnswerOutput getStructuredAnswer(
            Long userId,
            Long groupId,
            String question,
            RetrievedEvidenceBundle evidenceBundle
    ) {
        Prompt userPrompt = createUserPrompt(question, evidenceBundle);
        try {
            return chatClient(userId).prompt(userPrompt)
                    .advisors(advisor -> advisor
                            .param("groupId", groupId)
                            .param(
                                    ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                    evidenceBundle.documents()
                            ))
                    .call()
                    .entity(KnowledgeAnswerOutput.class);
        } catch (RuntimeException exception) {
            log.warn(
                    "QA structured output failed, fallback to raw content. groupId={}, evidenceCount={}",
                    groupId,
                    evidenceBundle.documents().size(),
                    exception
            );
            return parseFallbackAnswer(userId, groupId, question, evidenceBundle);
        }
    }

    private KnowledgeAnswerOutput parseFallbackAnswer(
            Long userId,
            Long groupId,
            String question,
            RetrievedEvidenceBundle evidenceBundle
    ) {
        Prompt userPrompt = createUserPrompt(question, evidenceBundle);
        try {
            String rawAnswer = chatClient(userId).prompt(userPrompt)
                    .advisors(advisor -> advisor
                            .param("groupId", groupId)
                            .param(
                                    ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY,
                                    evidenceBundle.documents()
                            ))
                    .call()
                    .content();
            log.info(
                    "QA raw answer fallback. groupId={}, evidenceCount={}, rawLength={}",
                    groupId,
                    evidenceBundle.documents().size(),
                    rawAnswer == null ? 0 : rawAnswer.length()
            );
            return answerParser.parse(rawAnswer);
        } catch (RuntimeException exception) {
            log.error(
                    "QA raw answer fallback failed. groupId={}, evidenceCount={}",
                    groupId,
                    evidenceBundle.documents().size(),
                    exception
            );
            return null;
        }
    }

    private ChatClient chatClient(Long userId) {
        ModelInvocationContext context = modelRuntimeService.resolveScenario(userId, ModelScenario.QA_ANSWER,
                new ModelRuntimeService.InvocationCorrelation(UUID.randomUUID().toString(), null, null, null));
        return ChatClient.builder(new GovernedChatModel(context, invocationDispatcher))
                .defaultSystem(qaSystemPromptTemplate.getTemplate())
                .defaultAdvisors(qaRetrievalAdvisor)
                .build();
    }

    private Prompt createUserPrompt(String question, RetrievedEvidenceBundle evidenceBundle) {
        EvidenceLevel evidenceLevel = evidenceBundle.evidenceLevel() == null
                ? EvidenceLevel.NONE
                : evidenceBundle.evidenceLevel();
        return qaUserPromptTemplate.create(Map.of(
                "question", question,
                "evidenceLevel", evidenceLevel.name(),
                "evidenceGuidance", evidenceBundle.evidenceGuidance()
        ));
    }
}
