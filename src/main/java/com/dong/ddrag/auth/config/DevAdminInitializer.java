package com.dong.ddrag.auth.config;

import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仅在 dev 环境确保一个可预测的管理员账号存在，避免本地调试还要额外手工开户。
 */
@Component
@Profile("dev")
public class DevAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final String username;
    private final String email;
    private final String displayName;
    private final String password;
    private final String userCode;

    public DevAdminInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            @Value("${ddrag.dev-admin.username:admin}") String username,
            @Value("${ddrag.dev-admin.email:admin@local.ddrag.test}") String email,
            @Value("${ddrag.dev-admin.display-name:开发环境管理员}") String displayName,
            @Value("${ddrag.dev-admin.password:Admin@123456}") String password,
            @Value("${ddrag.dev-admin.user-code:admin}") String userCode
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.username = username.trim();
        this.email = email.trim();
        this.displayName = displayName.trim();
        this.password = password;
        this.userCode = userCode.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long userId = jdbcTemplate.query(
                "select id from users where username = ? order by id limit 1",
                resultSet -> resultSet.next() ? resultSet.getLong("id") : null,
                username
        );
        if (userId == null) {
            jdbcTemplate.update(
                    """
                    insert into users (
                        user_code, username, email, display_name, password_hash,
                        system_role, status, must_change_password, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    """,
                    userCode,
                    username,
                    email,
                    displayName,
                    passwordHasher.hash(password),
                    SystemRole.ADMIN.name(),
                    UserStatus.ACTIVE.name(),
                    false
            );
            log.info("Dev admin initialized. username={}", username);
            return;
        }
        jdbcTemplate.update(
                """
                update users
                set email = ?,
                    display_name = ?,
                    password_hash = ?,
                    system_role = ?,
                    status = ?,
                    must_change_password = false,
                    updated_at = now()
                where id = ?
                """,
                email,
                displayName,
                passwordHasher.hash(password),
                SystemRole.ADMIN.name(),
                UserStatus.ACTIVE.name(),
                userId
        );
        log.info("Dev admin refreshed. username={}", username);
    }
}
