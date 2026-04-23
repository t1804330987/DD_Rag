package com.dong.ddrag.groupmembership;
import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupJoinRequestControllerTest {
    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_join_request_" + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-join-request-1234567890";
    private static final String PRODUCT_TEAM_CODE = "product-team";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;
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
    void resetMutableData() {
        jdbcTemplate.update("delete from group_join_requests");
        jdbcTemplate.update("delete from group_invitations");
        jdbcTemplate.update("delete from group_memberships");
        jdbcTemplate.update("delete from groups where id > 2002");
        jdbcTemplate.update("delete from users where id > 1002");
        jdbcTemplate.update("""
                insert into group_memberships (user_id, group_id, role, created_at, updated_at)
                values (1001, 2001, 'OWNER', now(), now()),
                       (1002, 2001, 'MEMBER', now(), now()),
                       (1002, 2002, 'OWNER', now(), now())
                """);
    }

    @Test
    void shouldCreatePendingJoinRequestByGroupCode() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        submitJoinRequest(1003L, "u1003", PRODUCT_TEAM_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber());
        assertThat(queryString("""
                select status from group_join_requests
                where group_id = 2001 and applicant_user_id = 1003
                """)).isEqualTo("PENDING");
    }

    @Test
    void shouldRejectJoinRequestWhenGroupCodeMissing() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        submitJoinRequest(1003L, "u1003", "missing-group")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("组织 ID 不存在"));
    }

    @Test
    void shouldRejectJoinRequestWhenAlreadyMember() throws Exception {
        submitJoinRequest(1002L, "u1002", PRODUCT_TEAM_CODE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该用户已经是知识库成员"));
    }

    @Test
    void shouldRejectJoinRequestWhenPendingInvitationExists() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        insertInvitation(2001L, 1001L, 1003L);
        submitJoinRequest(1003L, "u1003", PRODUCT_TEAM_CODE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该知识库已有待处理邀请，请先处理邀请"));
    }

    @Test
    void shouldRejectDuplicatePendingJoinRequest() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        insertJoinRequest(2001L, 1003L);
        submitJoinRequest(1003L, "u1003", PRODUCT_TEAM_CODE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该用户已有待处理加入申请，请先等待审批"));
    }

    @Test
    void shouldListMyJoinRequests() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        insertUser(1004L, "u1004", "测试用户丁");
        insertJoinRequest(2001L, 1003L);
        insertJoinRequest(2002L, 1004L);
        mockMvc.perform(get("/api/groups/join-requests/my").header(HttpHeaders.AUTHORIZATION, bearerToken(1003L, "u1003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].groupCode").value(PRODUCT_TEAM_CODE))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void shouldListPendingJoinRequestsByOwnerOnly() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        insertUser(1004L, "u1004", "测试用户丁");
        insertJoinRequest(2001L, 1003L);
        mockMvc.perform(get("/api/groups/2001/join-requests").header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].applicantUserId").value(1003))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        mockMvc.perform(get("/api/groups/2001/join-requests").header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组 OWNER"));
        mockMvc.perform(get("/api/groups/2001/join-requests").header(HttpHeaders.AUTHORIZATION, bearerToken(1004L, "u1004")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组成员"));
    }

    @Test
    void shouldApproveJoinRequestAndCreateMember() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        long requestId = insertJoinRequest(2001L, 1003L);
        decide(requestId, "approve").andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(queryString("select status from group_join_requests where id = ?", requestId)).isEqualTo("APPROVED");
        assertThat(queryLong("""
                select count(1) from group_join_requests
                where id = ? and decided_by_user_id = 1001 and decided_at is not null
                """, requestId)).isEqualTo(1L);
        assertThat(queryString("select role from group_memberships where group_id = 2001 and user_id = 1003"))
                .isEqualTo("MEMBER");
    }

    @Test
    void shouldRejectJoinRequestWithoutMembership() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        long requestId = insertJoinRequest(2001L, 1003L);
        decide(requestId, "reject").andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(queryString("select status from group_join_requests where id = ?", requestId)).isEqualTo("REJECTED");
        assertThat(queryLong("select count(1) from group_memberships where group_id = 2001 and user_id = 1003"))
                .isZero();
    }

    @Test
    void shouldRejectRepeatedDecision() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        long requestId = insertJoinRequest(2001L, 1003L);
        decide(requestId, "approve").andExpect(status().isOk());
        decide(requestId, "reject")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("申请已处理"));
    }

    @Test
    void shouldRejectAdminWhenCreatingJoinRequest() throws Exception {
        insertUser(9001L, "admin", "系统管理员", SystemRole.ADMIN);
        mockMvc.perform(post("/api/groups/join-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(9001L, "admin", SystemRole.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content(joinRequestJson(PRODUCT_TEAM_CODE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("系统管理员不能访问普通业务区"));
    }

    @AfterAll
    void cleanupDatabase() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
        executeOnAdminDatabase(connection -> {
            connection.createStatement().execute("""
                    select pg_terminate_backend(pid)
                    from pg_stat_activity
                    where datname = '%s' and pid <> pg_backend_pid()
                    """.formatted(TEST_DATABASE));
            connection.createStatement().execute("drop database if exists " + TEST_DATABASE);
        });
    }
    private ResultActions submitJoinRequest(long userId, String userCode, String groupCode) throws Exception {
        return mockMvc.perform(post("/api/groups/join-requests")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId, userCode))
                .contentType(APPLICATION_JSON)
                .content(joinRequestJson(groupCode)));
    }

    private ResultActions decide(long requestId, String action) throws Exception {
        return mockMvc.perform(post("/api/groups/2001/join-requests/{requestId}/{action}", requestId, action)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")));
    }

    private void insertUser(Long userId, String userCode, String displayName) {
        insertUser(userId, userCode, displayName, SystemRole.USER);
    }

    private void insertUser(Long userId, String userCode, String displayName, SystemRole systemRole) {
        jdbcTemplate.update("""
                insert into users (
                    id, user_code, username, email, display_name,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 'ACTIVE', false, now(), now())
                """, userId, userCode, userCode, userCode + "@local.ddrag.test", displayName, systemRole.name());
    }

    private String bearerToken(long userId, String userCode) {
        return bearerToken(userId, userCode, SystemRole.USER);
    }

    private String bearerToken(long userId, String userCode, SystemRole systemRole) {
        return "Bearer " + jwtAccessTokenService.issueToken(
                new JwtAccessTokenService.TokenSubject(userId, userCode, "用户-" + userCode, systemRole, false)
        );
    }
    private void insertInvitation(Long groupId, Long inviterUserId, Long inviteeUserId) {
        jdbcTemplate.update("""
                insert into group_invitations (group_id, inviter_user_id, invitee_user_id, status, created_at, updated_at)
                values (?, ?, ?, 'PENDING', now(), now())
                """, groupId, inviterUserId, inviteeUserId);
    }

    private long insertJoinRequest(Long groupId, Long applicantUserId) {
        jdbcTemplate.update("""
                insert into group_join_requests (group_id, applicant_user_id, status, created_at, updated_at)
                values (?, ?, 'PENDING', now(), now())
                """, groupId, applicantUserId);
        return queryLong("""
                select id from group_join_requests
                where group_id = ? and applicant_user_id = ?
                """, groupId, applicantUserId);
    }

    private Long queryLong(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private String queryString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private String joinRequestJson(String groupCode) {
        return "{\"groupCode\":\"" + groupCode + "\"}";
    }
    private static void createDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> connection.createStatement().execute("create database " + databaseName));
    }

    private static void executeOnAdminDatabase(SqlConsumer sqlConsumer) {
        try (Connection connection = DriverManager.getConnection(ADMIN_DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD)) {
            sqlConsumer.accept(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to manage temporary join request test database", exception);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
