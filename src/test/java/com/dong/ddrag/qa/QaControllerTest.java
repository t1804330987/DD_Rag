package com.dong.ddrag.qa;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.model.KnowledgeAnswerOutput;
import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import com.dong.ddrag.qa.rag.RetrievedEvidenceBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Answers;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QaControllerTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_qa_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-qa-controller-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockBean
    private PgVectorStore pgVectorStore;

    @MockBean(name = "qaChatClient", answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient qaChatClient;

    @MockBean
    private ReadyChunkDocumentRetriever readyChunkDocumentRetriever;

    @MockBean
    private DocumentIngestionProcessor documentIngestionProcessor;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        createDatabase(TEST_DATABASE);
        String jdbcUrl = "jdbc:postgresql://localhost:5433/" + TEST_DATABASE;
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> DATABASE_USERNAME);
        registry.add("spring.datasource.password", () -> DATABASE_PASSWORD);
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> DATABASE_USERNAME);
        registry.add("spring.flyway.password", () -> DATABASE_PASSWORD);
        registry.add("ddrag.auth.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add("spring.ai.dashscope.api-key", () -> "test-dashscope-key");
    }

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("delete from ingestion_jobs");
        jdbcTemplate.update("delete from document_chunks");
        jdbcTemplate.update("delete from documents");
        given(pgVectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of());
    }

    @Test
    void shouldRejectQaForNonMember() throws Exception {
        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2002L, "研发规范是什么？"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组成员"));
    }

    @Test
    void shouldAllowMemberToAskWithinReadableGroup() throws Exception {
        given(readyChunkDocumentRetriever.retrieveEvidence(2001L, "产品迭代周期多久？"))
                .willReturn(RetrievedEvidenceBundle.empty());

        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2001L, "产品迭代周期多久？"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false))
                .andExpect(jsonPath("$.reasonCode").value("INSUFFICIENT_EVIDENCE"));

        verifyNoInteractions(qaChatClient);
    }

    @Test
    void shouldRejectQaImmediatelyAfterMembershipRemoved() throws Exception {
        jdbcTemplate.update("delete from group_memberships where user_id = ? and group_id = ?", 1002L, 2001L);

        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2001L, "产品迭代周期多久？"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组成员"));
    }

    @Test
    void shouldReturnAnsweredFalseWhenEvidenceIsInsufficient() throws Exception {
        given(readyChunkDocumentRetriever.retrieveEvidence(2001L, "产品迭代周期多久？"))
                .willReturn(RetrievedEvidenceBundle.empty());

        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2001L, "产品迭代周期多久？"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false))
                .andExpect(jsonPath("$.reasonCode").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.citations.length()").value(0));

        verifyNoInteractions(qaChatClient);
    }

    @Test
    void shouldReturnAnswerAndCitationsWhenEvidenceIsSufficient() throws Exception {
        given(readyChunkDocumentRetriever.retrieveEvidence(2001L, "产品团队如何安排迭代？")).willReturn(bundle(
                EvidenceLevel.SUFFICIENT,
                List.of(
                        retrievedDocument("E1", 3001L, 4001L, 0, 0.82D, "产品手册.md", "产品团队每两周发布一次。"),
                        retrievedDocument("E2", 3002L, 4002L, 1, 0.71D, "团队规范.md", "每个迭代结束后进行复盘。")
                )
        ));
        given(qaChatClient.prompt()
                .user(any(java.util.function.Consumer.class))
                .advisors(any(java.util.function.Consumer.class))
                .call()
                .entity(KnowledgeAnswerOutput.class)).willReturn(
                        new KnowledgeAnswerOutput(
                                true,
                                "根据当前资料，产品团队每两周发布一次。 每个迭代结束后进行复盘。",
                                null,
                                null
                        )
                );
        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2001L, "产品团队如何安排迭代？"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.answer").value(
                        "根据当前资料，产品团队每两周发布一次。 每个迭代结束后进行复盘。"
                ))
                .andExpect(jsonPath("$.citations.length()").value(2))
                .andExpect(jsonPath("$.citations[0].fileName").value("产品手册.md"))
                .andExpect(jsonPath("$.citations[1].fileName").value("团队规范.md"));
    }

    @Test
    void shouldExcludeSoftDeletedDocumentsFromCitations() throws Exception {
        given(readyChunkDocumentRetriever.retrieveEvidence(2001L, "线上流程由谁维护？")).willReturn(bundle(
                EvidenceLevel.SUFFICIENT,
                List.of(
                        retrievedDocument("E1", 3001L, 4001L, 0, 0.83D, "在线文档.md", "线上流程由产品团队维护。"),
                        retrievedDocument("E2", 3003L, 4003L, 2, 0.70D, "补充说明.md", "补充说明会在周会上同步。")
                )
        ));
        given(qaChatClient.prompt()
                .user(any(java.util.function.Consumer.class))
                .advisors(any(java.util.function.Consumer.class))
                .call()
                .entity(KnowledgeAnswerOutput.class)).willReturn(
                        new KnowledgeAnswerOutput(
                                true,
                                "线上流程由产品团队维护，补充说明会在周会上同步。",
                                null,
                                null
                        )
                );
        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2001L, "线上流程由谁维护？"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.citations.length()").value(2))
                .andExpect(jsonPath("$.citations[0].fileName").value("在线文档.md"))
                .andExpect(jsonPath("$.citations[1].fileName").value("补充说明.md"));
    }

    @Test
    void shouldExcludeNotReadyDocumentsFromCitations() throws Exception {
        given(readyChunkDocumentRetriever.retrieveEvidence(2001L, "哪些文档可以作为回答依据？")).willReturn(bundle(
                EvidenceLevel.SUFFICIENT,
                List.of(
                        retrievedDocument("E1", 3001L, 4001L, 0, 0.80D, "已就绪文档.md", "产品文档已经完成发布。"),
                        retrievedDocument("E2", 3003L, 4003L, 2, 0.65D, "边界文档.md", "边界分数证据同样可以回答。")
                )
        ));
        given(qaChatClient.prompt()
                .user(any(java.util.function.Consumer.class))
                .advisors(any(java.util.function.Consumer.class))
                .call()
                .entity(KnowledgeAnswerOutput.class)).willReturn(
                        new KnowledgeAnswerOutput(
                                true,
                                "已就绪文档和边界分数证据都可以作为回答依据。",
                                null,
                                null
                        )
                );
        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2001L, "哪些文档可以作为回答依据？"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.citations.length()").value(2))
                .andExpect(jsonPath("$.citations[0].fileName").value("已就绪文档.md"))
                .andExpect(jsonPath("$.citations[1].fileName").value("边界文档.md"));
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
    }

    private String requestBody(Long groupId, String question) throws Exception {
        return objectMapper.writeValueAsString(Map.of("groupId", groupId, "question", question));
    }

    private String bearerToken(long userId, String userCode) {
        return "Bearer " + jwtAccessTokenService.issueToken(
                new JwtAccessTokenService.TokenSubject(
                        userId,
                        userCode,
                        "用户-" + userCode,
                        SystemRole.USER,
                        false
                )
        );
    }

    private Document retrievedDocument(
            String evidenceId,
            Long documentId,
            Long chunkId,
            int chunkIndex,
            double score,
            String fileName,
            String chunkText
    ) {
        return Document.builder()
                .id(evidenceId)
                .text(chunkText)
                .metadata(Map.of(
                        "evidenceId", evidenceId,
                        "groupId", 2001L,
                        "documentId", documentId,
                        "chunkId", chunkId,
                        "chunkIndex", chunkIndex,
                        "fileName", fileName,
                        "score", score
                ))
                .build();
    }

    private RetrievedEvidenceBundle bundle(EvidenceLevel level, List<Document> documents) {
        return new RetrievedEvidenceBundle(documents, level, "测试用证据指导");
    }

    private void closeDataSource() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    private static void createDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> connection.createStatement().execute("create database " + databaseName));
    }

    private static void dropDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> {
            connection.createStatement().execute(
                    """
                    select pg_terminate_backend(pid)
                    from pg_stat_activity
                    where datname = '%s' and pid <> pg_backend_pid()
                    """.formatted(databaseName)
            );
            connection.createStatement().execute("drop database if exists " + databaseName);
        });
    }

    private static void executeOnAdminDatabase(SqlConsumer sqlConsumer) {
        try (Connection connection = DriverManager.getConnection(
                ADMIN_DATABASE_URL,
                DATABASE_USERNAME,
                DATABASE_PASSWORD
        )) {
            sqlConsumer.accept(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to manage temporary qa test database", exception);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
