package com.dong.ddrag.document;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.storage.service.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.doNothing;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentControllerTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_document_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-document-controller-1234567890";

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
    private ObjectStorageService objectStorageService;

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
    void resetDocuments() {
        jdbcTemplate.update("delete from ingestion_jobs");
        jdbcTemplate.update("delete from document_chunks");
        jdbcTemplate.update("delete from documents");
        given(objectStorageService.getDefaultBucket()).willReturn("test-bucket");
        doNothing().when(documentIngestionProcessor).process(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldUploadDocumentForGroupOwner() throws Exception {
        mockMvc.perform(buildUploadRequest("u1001", 2001L, "需求说明.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/api/documents")
                        .param("groupId", "2001")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("需求说明.md"))
                .andExpect(jsonPath("$[0].status").value("READY"));

        assertThat(queryJobCount(documentIdFromLatestDocument())).isZero();
    }

    @Test
    void shouldRejectUploadForGroupMemberWithoutOwnerRole() throws Exception {
        mockMvc.perform(buildUploadRequest("u1002", 2001L, "成员越权上传.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组 OWNER"));
    }

    @Test
    void shouldRejectUploadForNonMember() throws Exception {
        mockMvc.perform(buildUploadRequest("u1001", 2002L, "越权文档.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组成员"));
    }

    @Test
    void shouldAllowMemberToListReadableDocumentsByGroupId() throws Exception {
        uploadDocument("u1001", 2001L, "产品说明.txt");
        uploadDocument("u1002", 2002L, "研发规范.txt");

        mockMvc.perform(get("/api/documents")
                        .param("groupId", "2001")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("产品说明.txt"))
                .andExpect(jsonPath("$[0].groupId").value(2001))
                .andExpect(jsonPath("$[0].uploaderUserCode").value("u1001"))
                .andExpect(jsonPath("$[0].uploaderDisplayName").value("测试用户甲"));
    }

    @Test
    void shouldFilterReadableDocumentsAcrossOwnedAndJoinedGroups() throws Exception {
        long joinedDocumentId = uploadDocument("u1001", 2001L, "产品排期说明.txt");
        long ownedReadyDocumentId = uploadDocument("u1002", 2002L, "研发规范说明.txt");
        long ownedProcessingDocumentId = uploadDocument("u1002", 2002L, "处理中研发任务.txt");
        updateDocumentUploadedAt(joinedDocumentId, LocalDateTime.of(2026, 4, 1, 10, 0));
        updateDocumentUploadedAt(ownedReadyDocumentId, LocalDateTime.of(2026, 4, 2, 10, 0));
        updateDocumentUploadedAt(ownedProcessingDocumentId, LocalDateTime.of(2026, 4, 3, 10, 0));
        updateDocumentStatus(ownedProcessingDocumentId, "PROCESSING");

        mockMvc.perform(get("/api/documents")
                        .param("groupRelation", "JOINED")
                        .param("fileName", "产品")
                        .param("uploaderUserId", "1001")
                        .param("status", "READY")
                        .param("uploadedFrom", "2026-04-01T00:00:00")
                        .param("uploadedTo", "2026-04-01T23:59:59")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documentId").value(joinedDocumentId))
                .andExpect(jsonPath("$[0].groupId").value(2001))
                .andExpect(jsonPath("$[0].uploaderUserCode").value("u1001"));

        mockMvc.perform(get("/api/documents")
                        .param("groupRelation", "OWNED")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupId").value(2002))
                .andExpect(jsonPath("$[1].groupId").value(2002));
    }

    @Test
    void shouldHideSoftDeletedDocumentFromList() throws Exception {
        long documentId = uploadDocument("u1001", 2001L, "待删除文档.txt");

        mockMvc.perform(delete("/api/documents/{documentId}", documentId)
                        .param("groupId", "2001")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/documents")
                        .param("groupId", "2001")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(queryDeletedFlag(documentId)).isTrue();
    }

    @Test
    void shouldRejectDeleteForGroupMemberWithoutOwnerRole() throws Exception {
        long documentId = uploadDocument("u1001", 2001L, "成员不可删除.txt");

        mockMvc.perform(delete("/api/documents/{documentId}", documentId)
                        .param("groupId", "2001")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组 OWNER"));
    }

    @Test
    void shouldReturnReadyPreviewForMember() throws Exception {
        long documentId = uploadDocument("u1001", 2001L, "可预览文档.txt");
        updateDocumentPreview(documentId, "这是清洗后的文档预览内容");

        mockMvc.perform(get("/api/documents/{documentId}/preview", documentId)
                        .param("groupId", "2001")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.fileName").value("可预览文档.txt"))
                .andExpect(jsonPath("$.previewText").value("这是清洗后的文档预览内容"));
    }

    @Test
    void shouldRejectPreviewWhenDocumentIsNotReady() throws Exception {
        long documentId = uploadDocument("u1001", 2001L, "处理中预览文档.txt");
        updateDocumentPreview(documentId, "处理中预览内容");
        updateDocumentStatus(documentId, "PROCESSING");

        mockMvc.perform(get("/api/documents/{documentId}/preview", documentId)
                        .param("groupId", "2001")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("文档尚未就绪，暂不可预览"));
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
    }

    private long uploadDocument(String userCode, Long groupId, String fileName) throws Exception {
        MvcResult mvcResult = mockMvc.perform(buildUploadRequest(userCode, groupId, fileName))
                .andExpect(status().isOk())
                .andReturn();
        return extractDocumentId(mvcResult);
    }

    private long extractDocumentId(MvcResult mvcResult) throws Exception {
        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        return root.path("data").asLong();
    }

    private boolean queryDeletedFlag(long documentId) {
        Boolean deleted = jdbcTemplate.queryForObject(
                "select deleted from documents where id = ?",
                Boolean.class,
                documentId
        );
        return Boolean.TRUE.equals(deleted);
    }

    private long documentIdFromLatestDocument() {
        Long documentId = jdbcTemplate.queryForObject(
                "select id from documents order by id desc limit 1",
                Long.class
        );
        return documentId == null ? -1L : documentId;
    }

    private int queryJobCount(long documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from ingestion_jobs where document_id = ?",
                Integer.class,
                documentId
        );
        return count == null ? 0 : count;
    }

    private void updateDocumentPreview(long documentId, String previewText) {
        jdbcTemplate.update(
                "update documents set preview_text = ?, updated_at = now() where id = ?",
                previewText,
                documentId
        );
    }

    private void updateDocumentStatus(long documentId, String status) {
        jdbcTemplate.update(
                "update documents set status = ?, updated_at = now() where id = ?",
                status,
                documentId
        );
    }

    private void updateDocumentUploadedAt(long documentId, LocalDateTime uploadedAt) {
        jdbcTemplate.update(
                "update documents set uploaded_at = ?, updated_at = now() where id = ?",
                Timestamp.valueOf(uploadedAt),
                documentId
        );
    }

    private org.springframework.test.web.servlet.RequestBuilder buildUploadRequest(
            String userCode,
            Long groupId,
            String fileName
    ) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                "hello document".getBytes(StandardCharsets.UTF_8)
        );
        return multipart("/api/documents/upload")
                .file(file)
                .param("groupId", String.valueOf(groupId))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userIdForUserCode(userCode), userCode));
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

    private long userIdForUserCode(String userCode) {
        return switch (userCode) {
            case "u1001" -> 1001L;
            case "u1002" -> 1002L;
            default -> throw new IllegalArgumentException("未知测试用户: " + userCode);
        };
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
            throw new IllegalStateException("Failed to manage temporary controller test database", exception);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
