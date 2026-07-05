package com.dong.ddrag.groupmembership;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupManagementControllerTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_group_manage_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-group-management-1234567890";

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
        jdbcTemplate.update("""
                insert into group_invitations (group_id, inviter_user_id, invitee_user_id, status, created_at, updated_at)
                values (2002, 1002, 1001, 'PENDING', now(), now())
                """);
    }

    @Test
    void shouldCreateGroupWithCreatorAsOwner() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"设计资料库","description":"沉淀设计规范"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        long groupId = extractDataId(result);

        assertThat(queryLong("select owner_user_id from groups where id = ?", groupId)).isEqualTo(1001L);
        assertThat(queryString("select description from groups where id = ?", groupId)).isEqualTo("沉淀设计规范");
        assertThat(queryString("select role from group_memberships where group_id = ? and user_id = 1001", groupId))
                .isEqualTo("OWNER");
    }

    @Test
    void shouldRejectAdminWhenCreatingBusinessGroup() throws Exception {
        insertUser(9001L, "admin", "系统管理员", SystemRole.ADMIN);

        mockMvc.perform(post("/api/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(9001L, "admin", SystemRole.ADMIN))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"管理员业务组","description":"admin should not enter business area"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系统管理员不能访问普通业务区"));
    }

    @Test
    void shouldInviteExistingUserByOwnerOnly() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");

        mockMvc.perform(post("/api/groups/2001/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"inviteeUserId\":1003}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber());

        assertThat(queryString("""
                select status from group_invitations
                where group_id = 2001 and invitee_user_id = 1003
                """)).isEqualTo("PENDING");
        mockMvc.perform(post("/api/groups/2001/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"inviteeUserId\":1003}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前用户不是目标群组 OWNER"));
    }

    @Test
    void shouldRejectInvitationWhenPendingJoinRequestExists() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        insertJoinRequest(2001L, 1003L);

        mockMvc.perform(post("/api/groups/2001/invitations")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"inviteeUserId\":1003}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该用户已有待处理加入申请，请先审批申请"));
    }

    @Test
    void shouldRejectInvitationAndBlockRepeatedDecision() throws Exception {
        long invitationId = seededInvitationId();

        mockMvc.perform(post("/api/invitations/{id}/reject", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(queryString("select status from group_invitations where id = ?", invitationId)).isEqualTo("REJECTED");
        assertThat(queryLong("select count(1) from group_invitations where id = ? and decided_at is not null", invitationId))
                .isEqualTo(1L);
        mockMvc.perform(post("/api/invitations/{id}/reject", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邀请已处理"));
    }

    @Test
    void shouldAcceptInvitationAndCreateMemberMembership() throws Exception {
        insertUser(1003L, "u1003", "测试用户丙");
        long invitationId = insertInvitation(2001L, 1001L, 1003L);

        mockMvc.perform(post("/api/invitations/{id}/accept", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1003L, "u1003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(queryString("select status from group_invitations where id = ?", invitationId)).isEqualTo("ACCEPTED");
        assertThat(queryString("select role from group_memberships where group_id = 2001 and user_id = 1003"))
                .isEqualTo("MEMBER");
    }

    @Test
    void shouldCancelPendingInvitationByOwner() throws Exception {
        long invitationId = seededInvitationId();

        mockMvc.perform(post("/api/invitations/{id}/cancel", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(queryString("select status from group_invitations where id = ?", invitationId)).isEqualTo("CANCELED");
    }

    @Test
    void shouldListAndRemoveMembersByOwner() throws Exception {
        mockMvc.perform(get("/api/groups/2001/members")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));

        mockMvc.perform(delete("/api/groups/2001/members/1002")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(queryLong("select count(1) from group_memberships where group_id = 2001 and user_id = 1002"))
                .isZero();
    }

    @Test
    void shouldAllowMemberLeaveButRejectOwnerLeave() throws Exception {
        mockMvc.perform(post("/api/groups/2001/leave")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(queryLong("select count(1) from group_memberships where group_id = 2001 and user_id = 1002"))
                .isZero();
        mockMvc.perform(post("/api/groups/2001/leave")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OWNER 不能退出自己的组"));
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
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
                new JwtAccessTokenService.TokenSubject(
                        userId,
                        userCode,
                        "用户-" + userCode,
                        systemRole,
                        false
                )
        );
    }

    private long insertInvitation(Long groupId, Long inviterUserId, Long inviteeUserId) {
        jdbcTemplate.update("""
                insert into group_invitations (group_id, inviter_user_id, invitee_user_id, status, created_at, updated_at)
                values (?, ?, ?, 'PENDING', now(), now())
                """, groupId, inviterUserId, inviteeUserId);
        return jdbcTemplate.queryForObject(
                "select id from group_invitations where group_id = ? and invitee_user_id = ?",
                Long.class,
                groupId,
                inviteeUserId
        );
    }

    private void insertJoinRequest(Long groupId, Long applicantUserId) {
        jdbcTemplate.update("""
                insert into group_join_requests (group_id, applicant_user_id, status, created_at, updated_at)
                values (?, ?, 'PENDING', now(), now())
                """, groupId, applicantUserId);
    }

    private long seededInvitationId() {
        return jdbcTemplate.queryForObject(
                "select id from group_invitations where group_id = 2002 and invitee_user_id = 1001",
                Long.class
        );
    }

    private long extractDataId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").asLong();
    }

    private Long queryLong(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private String queryString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
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
