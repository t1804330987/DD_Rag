package com.dong.ddrag.groupmembership;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.auth.security.JwtAuthenticationFilter;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupQueryControllerTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_group_query_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-group-query-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private GroupMembershipService groupMembershipService;

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

    @Test
    void shouldReturnOwnedJoinedGroupsAndPendingInvitationsForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/groups/my").header(HttpHeaders.AUTHORIZATION, bearerToken(1001L, "u1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedGroups.length()").value(1))
                .andExpect(jsonPath("$.ownedGroups[0].groupId").value(2001))
                .andExpect(jsonPath("$.ownedGroups[0].groupCode").value("product-team"))
                .andExpect(jsonPath("$.ownedGroups[0].groupName").value("产品团队"))
                .andExpect(jsonPath("$.joinedGroups.length()").value(0))
                .andExpect(jsonPath("$.pendingInvitations.length()").value(1))
                .andExpect(jsonPath("$.pendingInvitations[0].groupId").value(2002))
                .andExpect(jsonPath("$.pendingInvitations[0].groupName").value("研发团队"))
                .andExpect(jsonPath("$.pendingInvitations[0].inviterUserId").value(1002))
                .andExpect(jsonPath("$.pendingInvitations[0].status").value("PENDING"));
    }

    @Test
    void shouldReturnOwnedAndJoinedGroupsForSecondAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/groups/my").header(HttpHeaders.AUTHORIZATION, bearerToken(1002L, "u1002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedGroups.length()").value(1))
                .andExpect(jsonPath("$.ownedGroups[0].groupId").value(2002))
                .andExpect(jsonPath("$.ownedGroups[0].groupCode").value("engineering-team"))
                .andExpect(jsonPath("$.ownedGroups[0].groupName").value("研发团队"))
                .andExpect(jsonPath("$.joinedGroups.length()").value(1))
                .andExpect(jsonPath("$.joinedGroups[0].groupId").value(2001))
                .andExpect(jsonPath("$.joinedGroups[0].groupCode").value("product-team"))
                .andExpect(jsonPath("$.joinedGroups[0].groupName").value("产品团队"))
                .andExpect(jsonPath("$.pendingInvitations.length()").value(0));
    }

    @Test
    void shouldRejectRequestWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/groups/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前请求未登录"));
    }

    @Test
    void shouldRejectRequestWithOnlyLegacyTestHeaderAfterCutover() throws Exception {
        mockMvc.perform(get("/api/groups/my").header("X-Test-User-Id", "u1001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前请求未登录"));
    }

    @Test
    void shouldRejectAdminWhenQueryingBusinessGroups() throws Exception {
        insertUser(9001L, "admin", "系统管理员", SystemRole.ADMIN);

        mockMvc.perform(get("/api/groups/my")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(9001L, "admin", SystemRole.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系统管理员不能访问普通业务区"));
    }

    @Test
    void shouldRejectMemberFromOwnerPermission() {
        MockHttpServletRequest request = requestForUser("u1002");

        assertThatThrownBy(() -> invokeRequireGroupOwner(request, 2001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户不是目标群组 OWNER");
    }

    @Test
    void shouldRejectInvitationWhenInviteeIsAlreadyMember() {
        MockHttpServletRequest request = requestForUser("u1001");

        assertThatThrownBy(() -> invokeCreatePendingInvitation(request, 2001L, 1002L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("被邀请人已是群组成员");
    }

    @Test
    void shouldRejectInvitationWhenPendingInvitationExists() {
        MockHttpServletRequest request = requestForUser("u1002");

        assertThatThrownBy(() -> invokeCreatePendingInvitation(request, 2002L, 1001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已存在待处理邀请");
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
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

    private MockHttpServletRequest requestForUser(String userCode) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        long userId = userIdForUserCode(userCode);
        request.setAttribute(
                JwtAuthenticationFilter.AUTHENTICATED_USER_REQUEST_ATTRIBUTE,
                new JwtAuthenticationFilter.AuthenticatedUser(
                        userId,
                        userCode,
                        "用户-" + userCode,
                        SystemRole.USER,
                        false
                )
        );
        return request;
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

    private void insertUser(Long userId, String userCode, String displayName, SystemRole systemRole) {
        jdbcTemplate.update("""
                insert into users (
                    id, user_code, username, email, display_name,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 'ACTIVE', false, now(), now())
                """, userId, userCode, userCode, userCode + "@local.ddrag.test", displayName, systemRole.name());
    }

    private long userIdForUserCode(String userCode) {
        return switch (userCode) {
            case "u1001" -> 1001L;
            case "u1002" -> 1002L;
            default -> throw new IllegalArgumentException("未知测试用户: " + userCode);
        };
    }

    private Object invokeRequireGroupOwner(MockHttpServletRequest request, Long groupId) {
        return invokeServiceMethod(
                "requireGroupOwner",
                new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class, Long.class},
                request,
                groupId
        );
    }

    private Object invokeCreatePendingInvitation(
            MockHttpServletRequest request,
            Long groupId,
            Long inviteeUserId
    ) {
        return invokeServiceMethod(
                "createPendingInvitation",
                new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class, Long.class, Long.class},
                request,
                groupId,
                inviteeUserId
        );
    }

    private Object invokeServiceMethod(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = GroupMembershipService.class.getMethod(methodName, parameterTypes);
            return method.invoke(groupMembershipService, args);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("缺少权限服务入口: " + methodName, exception);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("无法调用权限服务入口: " + methodName, exception);
        } catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            if (targetException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("权限服务入口抛出非运行时异常: " + methodName, targetException);
        }
    }
}
