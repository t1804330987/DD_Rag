package com.dong.ddrag.modelplatform;

import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.assistant.service.AssistantTurnRequestService;
import com.dong.ddrag.modelplatform.mapper.AssistantInstructionProfileMapper;
import com.dong.ddrag.modelplatform.mapper.AssistantInstructionProfileVersionMapper;
import com.dong.ddrag.modelplatform.mapper.AssistantTurnRequestMapper;
import com.dong.ddrag.modelplatform.mapper.ModelCallLedgerMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionGrantMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionMapper;
import com.dong.ddrag.modelplatform.mapper.ModelConnectionModelMapper;
import com.dong.ddrag.modelplatform.mapper.ModelScenarioRouteMapper;
import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileEntity;
import com.dong.ddrag.modelplatform.model.entity.AssistantInstructionProfileVersionEntity;
import com.dong.ddrag.modelplatform.model.entity.AssistantTurnRequestEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelCallLedgerEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionGrantEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelScenarioRouteEntity;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelPlatformPersistenceTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_model_platform_" + UUID.randomUUID().toString().replace("-", "");
    private static final String API_KEY = "sk-secret-model-platform-test";
    private static final long USER_ID = 1001L;

    @Autowired private ModelConnectionMapper connectionMapper;
    @Autowired private ModelConnectionModelMapper modelMapper;
    @Autowired private AssistantSessionMapper assistantSessionMapper;
    @Autowired private ModelConnectionGrantMapper grantMapper;
    @Autowired private ModelScenarioRouteMapper routeMapper;
    @Autowired private AssistantInstructionProfileMapper profileMapper;
    @Autowired private AssistantInstructionProfileVersionMapper profileVersionMapper;
    @Autowired private ModelCallLedgerMapper ledgerMapper;
    @Autowired private AssistantTurnRequestMapper turnRequestMapper;
    @Autowired private AssistantTurnRequestService assistantTurnRequestService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @MockBean private DocumentIngestionProcessor documentIngestionProcessor;

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
        registry.add("ddrag.auth.jwt-secret", () -> "test-model-platform-jwt-secret-32-bytes");
        registry.add("ddrag.security.api-key-encryption-secret", () -> "test-api-key-encryption-secret-32b");
        registry.add("spring.ai.dashscope.api-key", () -> "test-dashscope-key");
    }

    @BeforeEach
    void cleanModelPlatformTables() {
        jdbcTemplate.update("delete from model_call_ledger");
        jdbcTemplate.update("delete from assistant_turn_requests");
        jdbcTemplate.update("delete from assistant_session_contexts");
        jdbcTemplate.update("delete from assistant_messages");
        jdbcTemplate.update("delete from assistant_sessions");
        jdbcTemplate.update("update assistant_instruction_profiles set current_version_id = null");
        jdbcTemplate.update("delete from assistant_instruction_profile_versions");
        jdbcTemplate.update("delete from assistant_instruction_profiles");
        jdbcTemplate.update("delete from model_scenario_routes");
        jdbcTemplate.update("delete from model_connection_grants");
        jdbcTemplate.update("delete from model_connection_models");
        jdbcTemplate.update("delete from model_connections");
    }

    @Test
    void shouldPersistGovernanceQueriesWithoutExposingApiKey() throws Exception {
        ModelConnectionEntity connection = platformConnection();
        assertThat(connectionMapper.insert(connection)).isEqualTo(1);
        assertThat(ModelConnectionEntity.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("getApiKeyPlaintext");
        assertThat(connection.readApiKeyPlaintext()).isEqualTo(API_KEY);
        assertThat(connection.toString()).doesNotContain(API_KEY);
        assertThat(new IllegalStateException("Connection failed: " + connection)).hasMessageNotContaining(API_KEY);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(connection))
                .doesNotContain(API_KEY)
                .doesNotContain("apiKeyPlaintext");

        String stored = jdbcTemplate.queryForObject(
                "select api_key_plaintext from model_connections where id = ?", String.class, connection.getId());
        assertThat(stored).startsWith("ENC$v1$").doesNotContain(API_KEY);
        assertThat(jdbcTemplate.queryForObject(
                "select credential_storage_type from model_connections where id = ?", String.class, connection.getId()))
                .isEqualTo("ENCRYPTED");
        ModelConnectionEntity reloaded = connectionMapper.selectFormalById(connection.getId());
        assertThat(reloaded.readApiKeyPlaintext()).isEqualTo(API_KEY);

        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);
        ModelConnectionGrantEntity grant = new ModelConnectionGrantEntity();
        grant.setConnectionId(connection.getId());
        grant.setGrantType("USER");
        grant.setGranteeUserId(USER_ID);
        grant.setCreatedAt(LocalDateTime.now());
        grantMapper.insert(grant);

        ModelScenarioRouteEntity route = new ModelScenarioRouteEntity();
        route.setScenario("QUERY_PLANNING");
        route.setConnectionId(connection.getId());
        route.setModelId(model.getId());
        route.setEnabled(true);
        route.setCreatedAt(LocalDateTime.now());
        route.setUpdatedAt(LocalDateTime.now());
        routeMapper.insert(route);

        assertThat(connectionMapper.selectByOwner("PLATFORM", null, "ACTIVE")).hasSize(1);
        assertThat(grantMapper.existsGrantForUser(connection.getId(), USER_ID)).isTrue();
        assertThat(routeMapper.selectFormalRouteByScenario("QUERY_PLANNING")).isNotNull();
        assertThat(modelMapper.selectFormalById(model.getId(), connection.getId())).isNotNull();

        jdbcTemplate.update("update model_connections set config_version = config_version + 1 where id = ?", connection.getId());
        assertThat(modelMapper.selectFormalById(model.getId(), connection.getId())).isNull();
        assertThat(routeMapper.selectFormalRouteByScenario("QUERY_PLANNING")).isNull();
    }

    @Test
    void shouldKeepLedgerHistoryAfterConnectionSoftDelete() {
        ModelConnectionEntity connection = platformConnection();
        connectionMapper.insert(connection);
        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);

        ModelCallLedgerEntity ledger = ledger(connection, model);
        ledgerMapper.insert(ledger);
        assertThat(ledgerMapper.selectByUserAndStartedAtBetween(
                USER_ID, ledger.getStartedAt().minusMinutes(1), ledger.getStartedAt().plusMinutes(1)))
                .hasSize(1);
        assertThat(ledgerMapper.selectForAdminUsage(USER_ID, "OPENAI", "gpt-test", "ASSISTANT_CHAT",
                "SUCCEEDED", ledger.getStartedAt().minusMinutes(1), ledger.getStartedAt().plusMinutes(1)))
                .hasSize(1);

        LocalDateTime deletedAt = LocalDateTime.now();
        assertThat(connectionMapper.softDeleteOwned(connection.getId(), "USER", USER_ID, deletedAt)).isZero();
        assertThat(connectionMapper.selectFormalById(connection.getId())).isNotNull();
        assertThat(connectionMapper.softDeleteOwned(connection.getId(), "PLATFORM", null, deletedAt)).isEqualTo(1);
        assertThat(connectionMapper.selectFormalById(connection.getId())).isNull();
        assertThat(modelMapper.selectFormalById(model.getId(), connection.getId())).isNull();

        ModelCallLedgerEntity historical = ledgerMapper.selectByInvocationId(ledger.getInvocationId());
        assertThat(historical).isNotNull();
        assertThat(historical.getProviderTypeSnapshot()).isEqualTo("OPENAI");
        assertThat(historical.getModelNameSnapshot()).isEqualTo("gpt-test");
        assertThat(historical.toString()).doesNotContain(API_KEY);
    }

    @Test
    void shouldHideCatalogModelAndRestoreItWhenProviderDiscoversItAgain() {
        ModelConnectionEntity connection = platformConnection();
        connectionMapper.insert(connection);
        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);

        assertThat(modelMapper.hideById(model.getId(), connection.getId(), LocalDateTime.now())).isEqualTo(1);
        assertThat(modelMapper.selectByConnectionId(connection.getId())).isEmpty();
        assertThat(modelMapper.selectFormalById(model.getId(), connection.getId())).isNull();

        ModelConnectionModelEntity discovered = new ModelConnectionModelEntity();
        discovered.setConnectionId(connection.getId());
        discovered.setModelName(model.getModelName());
        discovered.setSourceType("DISCOVERED");
        discovered.setTestStatus("UNVERIFIED");
        discovered.setEnabled(false);
        discovered.setCreatedAt(LocalDateTime.now());
        discovered.setUpdatedAt(LocalDateTime.now());
        modelMapper.upsertCatalogModel(discovered);

        ModelConnectionModelEntity restored = modelMapper.selectById(model.getId(), connection.getId());
        assertThat(restored).isNotNull();
        assertThat(restored.getTestStatus()).isEqualTo("UNVERIFIED");
        assertThat(restored.getTestedConfigVersion()).isNull();
        assertThat(restored.getEnabled()).isFalse();
    }

    @Test
    void shouldKeepEnabledModelEnabledOnlyWhenRetestPasses() {
        ModelConnectionEntity connection = platformConnection();
        connectionMapper.insert(connection);
        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);

        assertThat(modelMapper.completeTestCas(model.getId(), connection.getId(), connection.getConfigVersion(),
                "PASSED", LocalDateTime.now())).isEqualTo(1);
        assertThat(modelMapper.selectById(model.getId(), connection.getId()).getEnabled()).isTrue();

        assertThat(modelMapper.completeTestCas(model.getId(), connection.getId(), connection.getConfigVersion(),
                "FAILED", LocalDateTime.now())).isEqualTo(1);
        assertThat(modelMapper.selectById(model.getId(), connection.getId()).getEnabled()).isFalse();
    }

    @Test
    void shouldPersistInitialAssistantSessionModelBinding() {
        ModelConnectionEntity connection = platformConnection();
        connectionMapper.insert(connection);
        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);
        AssistantSessionEntity session = new AssistantSessionEntity();
        session.setUserId(USER_ID);
        session.setTitle("新会话");
        session.setStatus("ACTIVE");
        session.setCurrentModelConnectionId(connection.getId());
        session.setCurrentModelId(model.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(session.getCreatedAt());

        assertThat(assistantSessionMapper.insert(session)).isEqualTo(1);

        AssistantSessionEntity persisted = assistantSessionMapper.selectByIdAndUserId(session.getId(), USER_ID);
        assertThat(persisted.getCurrentModelConnectionId()).isEqualTo(connection.getId());
        assertThat(persisted.getCurrentModelId()).isEqualTo(model.getId());
    }

    @Test
    void shouldCasLedgerTerminalStateAndAggregateEachInvocation() {
        ModelConnectionEntity connection = platformConnection();
        connectionMapper.insert(connection);
        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);

        ModelCallLedgerEntity first = ledger(connection, model);
        first.setInvocationId("invocation-cas-1");
        first.setTurnId("turn-shared");
        first.setRequestId("request-shared");
        first.setLogicalStatus("RUNNING");
        first.setTransportStatus("ACTIVE");
        first.setFinishedAt(null);
        ledgerMapper.insert(first);

        ModelCallLedgerEntity second = ledger(connection, model);
        second.setInvocationId("invocation-cas-2");
        second.setTurnId("turn-shared");
        second.setRequestId("request-shared");
        second.setLogicalStatus("RUNNING");
        second.setTransportStatus("ACTIVE");
        second.setFinishedAt(null);
        ledgerMapper.insert(second);

        assertThat(ledgerMapper.completeIfRunning(first.getInvocationId(), "SUCCEEDED", "TERMINATED",
                10L, 20L, 30L, 100L, null, null, null)).isEqualTo(1);
        assertThat(ledgerMapper.completeIfRunning(first.getInvocationId(), "FAILED", "TERMINATED",
                null, null, null, 110L, null, "LATE_ERROR", "late callback")).isZero();
        assertThat(ledgerMapper.completeIfRunning(second.getInvocationId(), "SUCCEEDED", "TERMINATED",
                5L, 7L, 12L, 80L, null, null, null)).isEqualTo(1);

        ModelCallLedgerMapper.UsageAggregateRow aggregate = ledgerMapper.aggregateUsage(USER_ID, "OPENAI",
                "gpt-test", "ASSISTANT_CHAT", "SUCCEEDED", "TERMINATED",
                first.getStartedAt().minusMinutes(1), first.getStartedAt().plusMinutes(1)).getFirst();
        assertThat(aggregate.getInvocationCount()).isEqualTo(2);
        assertThat(aggregate.getTotalTokens()).isEqualTo(42);
        assertThat(aggregate.getDurationMs()).isEqualTo(180);
        assertThat(ledgerMapper.selectForAdminUsage(USER_ID, null, null, null, null,
                first.getStartedAt().minusMinutes(1), first.getStartedAt().plusMinutes(1)))
                .extracting(ModelCallLedgerEntity::getInvocationId)
                .containsExactlyInAnyOrder("invocation-cas-1", "invocation-cas-2");
    }

    @Test
    void shouldOnlySelectAndCloseSupportedStaleLedgerStates() {
        ModelConnectionEntity connection = platformConnection();
        connectionMapper.insert(connection);
        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);
        LocalDateTime old = LocalDateTime.now().minusMinutes(10);

        ModelCallLedgerEntity active = ledger(connection, model);
        active.setInvocationId("stale-active");
        active.setLogicalStatus("RUNNING");
        active.setTransportStatus("ACTIVE");
        active.setStartedAt(old);
        active.setFinishedAt(null);
        ledgerMapper.insert(active);

        ModelCallLedgerEntity detached = ledger(connection, model);
        detached.setInvocationId("stale-detached");
        detached.setLogicalStatus("CANCEL_REQUESTED");
        detached.setTransportStatus("DETACHED");
        detached.setStartedAt(old);
        detached.setFinishedAt(null);
        ledgerMapper.insert(detached);

        ModelCallLedgerEntity completed = ledger(connection, model);
        completed.setInvocationId("completed");
        completed.setStartedAt(old);
        ledgerMapper.insert(completed);

        assertThat(ledgerMapper.selectStaleCandidates(LocalDateTime.now().minusMinutes(6)))
                .extracting(ModelCallLedgerEntity::getInvocationId)
                .containsExactly("stale-active", "stale-detached");
        assertThat(ledgerMapper.hardTimeoutIfUnfinished("stale-detached", 360_000L,
                "PROCESS_INTERRUPTED", LocalDateTime.now())).isEqualTo(1);
        ModelCallLedgerEntity reconciled = ledgerMapper.selectByInvocationId("stale-detached");
        assertThat(reconciled.getLogicalStatus()).isEqualTo("TIMEOUT");
        assertThat(reconciled.getTransportStatus()).isEqualTo("HARD_TIMEOUT");
        assertThat(ledgerMapper.hardTimeoutIfUnfinished("completed", 360_000L,
                "PROCESS_INTERRUPTED", LocalDateTime.now())).isZero();
    }

    @Test
    void shouldNotTreatUserConnectionAsPlatformGrantOrScenarioRoute() {
        ModelConnectionEntity connection = platformConnection();
        connection.setOwnerType("USER");
        connection.setOwnerUserId(USER_ID);
        connection.setName("private-openai");
        connectionMapper.insert(connection);

        ModelConnectionModelEntity model = testedModel(connection.getId());
        modelMapper.insert(model);

        ModelConnectionGrantEntity grant = new ModelConnectionGrantEntity();
        grant.setConnectionId(connection.getId());
        grant.setGrantType("ALL_BUSINESS_USERS");
        grant.setCreatedAt(LocalDateTime.now());
        grantMapper.insert(grant);

        ModelScenarioRouteEntity route = new ModelScenarioRouteEntity();
        route.setScenario("SESSION_SUMMARY");
        route.setConnectionId(connection.getId());
        route.setModelId(model.getId());
        route.setEnabled(true);
        route.setCreatedAt(LocalDateTime.now());
        route.setUpdatedAt(LocalDateTime.now());
        routeMapper.insert(route);

        assertThat(grantMapper.existsGrantForUser(connection.getId(), USER_ID)).isFalse();
        assertThat(routeMapper.selectFormalRouteByScenario("SESSION_SUMMARY")).isNull();
    }

    @Test
    void shouldPersistInstructionVersionsAndTurnRequestLookup() {
        AssistantInstructionProfileEntity profile = new AssistantInstructionProfileEntity();
        profile.setUserId(USER_ID);
        profile.setName("Concise");
        profile.setEnabled(true);
        profile.setDefault(true);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(profile);

        AssistantInstructionProfileVersionEntity version = new AssistantInstructionProfileVersionEntity();
        version.setProfileId(profile.getId());
        version.setVersion(1);
        version.setContent("Answer concisely");
        version.setCreatedAt(LocalDateTime.now());
        profileVersionMapper.insert(version);
        profileMapper.updateCurrentVersion(profile.getId(), USER_ID, version.getId(), LocalDateTime.now());

        assertThat(profileMapper.selectEnabledByUserId(USER_ID)).singleElement()
                .extracting(AssistantInstructionProfileEntity::getCurrentVersionId)
                .isEqualTo(version.getId());
        assertThat(profileVersionMapper.selectByProfileIdAndUserId(profile.getId(), USER_ID)).hasSize(1);
        assertThat(profileVersionMapper.selectByProfileIdAndUserId(profile.getId(), USER_ID + 1)).isEmpty();

        AssistantTurnRequestEntity request = new AssistantTurnRequestEntity();
        request.setUserId(USER_ID);
        request.setRequestId("req-1");
        request.setTurnId("turn-1");
        request.setStatus("RUNNING");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        turnRequestMapper.insert(request);
        assertThat(turnRequestMapper.selectByUserIdAndRequestId(USER_ID, "req-1").getId())
                .isEqualTo(request.getId());
    }

    @Test
    void shouldKeepExactlyOneTurnRequestWhenSameUserRequestIsInsertedConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> insertSameTurnRequest(ready, start));
            Future<Boolean> second = executor.submit(() -> insertSameTurnRequest(ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get() || second.get()).isTrue();
            assertThat(first.get() && second.get()).isFalse();
            assertThat(turnRequestMapper.selectByUserIdAndRequestId(USER_ID, "concurrent-request")).isNotNull();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from assistant_turn_requests where user_id = ? and request_id = ?",
                    Long.class, USER_ID, "concurrent-request"
            )).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPrepareOnlyOneSessionAndUserMessageDuringRealRequestRace() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AssistantChatRequest request = new AssistantChatRequest(
                null, "请帮我总结这段内容", AssistantToolMode.CHAT, null, "real-request-race"
        );
        try {
            Future<AssistantTurnRequestService.PreparedTurn> first = executor.submit(
                    () -> prepareSameRequest(ready, start, request)
            );
            Future<AssistantTurnRequestService.PreparedTurn> second = executor.submit(
                    () -> prepareSameRequest(ready, start, request)
            );
            ready.await();
            start.countDown();

            AssistantTurnRequestService.PreparedTurn firstResult = first.get();
            AssistantTurnRequestService.PreparedTurn secondResult = second.get();
            assertThat(List.of(firstResult, secondResult).stream().filter(AssistantTurnRequestService.PreparedTurn::isAccepted))
                    .hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from assistant_turn_requests where user_id = ? and request_id = ?",
                    Long.class, USER_ID, "real-request-race"
            )).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("select count(*) from assistant_sessions where user_id = ?", Long.class, USER_ID))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("select count(*) from assistant_messages", Long.class)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean insertSameTurnRequest(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        AssistantTurnRequestEntity request = new AssistantTurnRequestEntity();
        request.setUserId(USER_ID);
        request.setRequestId("concurrent-request");
        request.setTurnId(UUID.randomUUID().toString());
        request.setStatus("RUNNING");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(request.getCreatedAt());
        ready.countDown();
        start.await();
        try {
            return turnRequestMapper.insert(request) == 1;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private AssistantTurnRequestService.PreparedTurn prepareSameRequest(
            CountDownLatch ready,
            CountDownLatch start,
            AssistantChatRequest request
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return assistantTurnRequestService.prepare(USER_ID, request);
    }

    private ModelConnectionEntity platformConnection() {
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setProviderType("OPENAI");
        entity.setOwnerType("PLATFORM");
        entity.setName("platform-openai");
        entity.setBaseUrl("https://example.invalid");
        entity.setApiKeyPlaintext(API_KEY);
        entity.setCredentialStorageType("ENCRYPTED");
        entity.setCredentialVersion(1);
        entity.setMaskedKeySuffix("test");
        entity.setStatus("ACTIVE");
        entity.setConfigVersion(1L);
        entity.setLastConnectionTestStatus("PASSED");
        entity.setLastConnectionTestAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private ModelConnectionModelEntity testedModel(Long connectionId) {
        ModelConnectionModelEntity entity = new ModelConnectionModelEntity();
        entity.setConnectionId(connectionId);
        entity.setModelName("gpt-test");
        entity.setSourceType("MANUAL");
        entity.setTestStatus("PASSED");
        entity.setTestedConfigVersion(1L);
        entity.setLastTestAt(LocalDateTime.now());
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private ModelCallLedgerEntity ledger(ModelConnectionEntity connection, ModelConnectionModelEntity model) {
        ModelCallLedgerEntity entity = new ModelCallLedgerEntity();
        entity.setInvocationId("invocation-1");
        entity.setUserId(USER_ID);
        entity.setScenario("ASSISTANT_CHAT");
        entity.setConnectionId(connection.getId());
        entity.setModelId(model.getId());
        entity.setProviderTypeSnapshot(connection.getProviderType());
        entity.setModelNameSnapshot(model.getModelName());
        entity.setConnectionNameSnapshot(connection.getName());
        entity.setOwnerTypeSnapshot(connection.getOwnerType());
        entity.setLogicalStatus("SUCCEEDED");
        entity.setTransportStatus("TERMINATED");
        entity.setStartedAt(LocalDateTime.now());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @AfterAll
    void cleanupDatabase() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
        dropDatabase(TEST_DATABASE);
    }

    private static void createDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> connection.createStatement().execute("create database " + databaseName));
    }

    private static void dropDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> {
            connection.createStatement().execute("select pg_terminate_backend(pid) from pg_stat_activity where datname = '"
                    + databaseName + "' and pid <> pg_backend_pid()");
            connection.createStatement().execute("drop database if exists " + databaseName);
        });
    }

    private static void executeOnAdminDatabase(SqlConsumer consumer) {
        try (Connection connection = DriverManager.getConnection(ADMIN_DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD)) {
            consumer.accept(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to manage temporary model platform database", exception);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
