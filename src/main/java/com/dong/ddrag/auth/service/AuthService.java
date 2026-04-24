package com.dong.ddrag.auth.service;

import com.dong.ddrag.auth.model.dto.RegisterRequest;
import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.auth.security.RefreshTokenService;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.identity.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "账号或密码错误";
    private static final String INVALID_PASSWORD_MESSAGE = "新密码必须至少 8 位，且同时包含字母和数字";
    private static final int MAX_LOGIN_ID_LENGTH = 128;
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> RESERVED_USERNAMES = Set.of("admin", "root", "null", "undefined", "system");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            JwtAccessTokenService jwtAccessTokenService,
            RefreshTokenService refreshTokenService,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthTokens login(String loginId, String password) {
        LoginCommand command = validateLoginCommand(loginId, password);
        UserAccount user = loadUserForLogin(command.loginId());
        ensureUserCanLogin(user, command.password());
        refreshTokenService.revokeActiveTokens(user.userId());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user.userId());
        updateSuccessfulLogin(user.userId());
        return new AuthTokens(
                user.userId(),
                issueAccessToken(user),
                refreshToken.refreshToken(),
                user.mustChangePassword()
        );
    }

    @Transactional
    public void register(RegisterRequest request) {
        RegisterCommand command = validateRegisterCommand(request);
        ensureUniqueIdentity(command.username(), command.email());
        jdbcTemplate.update(
                """
                insert into users (
                    user_code, username, email, display_name, password_hash,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                command.username(),
                command.username(),
                command.email(),
                command.displayName(),
                passwordHasher.hash(command.password()),
                SystemRole.USER.name(),
                UserStatus.ACTIVE.name(),
                false
        );
    }

    @Transactional
    public void resetPasswordByIdentity(String username, String email, String newPassword) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeRequiredValue(email, "邮箱不能为空", "邮箱长度不能超过 128", MAX_EMAIL_LENGTH);
        validateRegisterPassword(newPassword);
        UserAccount user = loadUserByUsernameAndEmail(normalizedUsername, normalizedEmail);
        int updated = jdbcTemplate.update(
                """
                update users
                set password_hash = ?, must_change_password = false, updated_at = now()
                where id = ?
                """,
                passwordHasher.hash(newPassword),
                user.userId()
        );
        if (updated == 0) {
            throw new BusinessException("账号信息不匹配");
        }
        refreshTokenService.revokeActiveTokens(user.userId());
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("refresh token 不存在或已失效");
        }
        RefreshTokenRecord activeToken = refreshTokenService.findActiveToken(refreshToken)
                .orElseThrow(() -> new BusinessException("refresh token 不存在或已失效"));
        UserAccount user = loadUserById(activeToken.userId());
        ensureRefreshAllowed(user);
        refreshTokenService.revokeToken(refreshToken);
        RefreshTokenService.IssuedRefreshToken nextRefreshToken = refreshTokenService.issueToken(user.userId());
        return new AuthTokens(
                user.userId(),
                issueAccessToken(user),
                nextRefreshToken.refreshToken(),
                user.mustChangePassword()
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenService.revokeToken(refreshToken);
    }

    public CurrentUserService.CurrentUser getCurrentUser(Long userId) {
        UserAccount user = loadUserById(userId);
        return new CurrentUserService.CurrentUser(
                user.userId(),
                user.userCode(),
                user.displayName(),
                user.systemRole(),
                user.mustChangePassword()
        );
    }

    private RegisterCommand validateRegisterCommand(RegisterRequest request) {
        if (request == null) {
            throw new BusinessException("注册请求不能为空");
        }
        String username = normalizeUsername(request.username());
        String email = normalizeRequiredValue(request.email(), "邮箱不能为空", "邮箱长度不能超过 128", MAX_EMAIL_LENGTH);
        String displayName = normalizeRequiredValue(
                request.displayName(),
                "显示名称不能为空",
                "显示名称长度不能超过 128",
                MAX_DISPLAY_NAME_LENGTH
        );
        validateRegisterPassword(request.password());
        return new RegisterCommand(username, email, displayName, request.password());
    }

    private LoginCommand validateLoginCommand(String loginId, String password) {
        String normalizedLoginId = normalizeLoginId(loginId);
        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("密码长度非法");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
        }
        return new LoginCommand(normalizedLoginId, password);
    }

    private String normalizeLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException("登录标识不能为空");
        }
        String normalizedLoginId = loginId.trim();
        if (normalizedLoginId.length() > MAX_LOGIN_ID_LENGTH) {
            throw new BusinessException("登录标识长度非法");
        }
        return normalizedLoginId;
    }

    private void validateRegisterPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(INVALID_PASSWORD_MESSAGE);
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("密码长度非法");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char current = password.charAt(i);
            if (Character.isLetter(current)) {
                hasLetter = true;
            }
            if (Character.isDigit(current)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(INVALID_PASSWORD_MESSAGE);
        }
    }

    private String normalizeUsername(String username) {
        String normalizedValue = normalizeRequiredValue(username, "用户名不能为空", "用户名长度不能超过 64", MAX_USERNAME_LENGTH);
        if (!USERNAME_PATTERN.matcher(normalizedValue).matches()) {
            throw new BusinessException("用户名不合法");
        }
        if (RESERVED_USERNAMES.contains(normalizedValue.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("用户名不合法");
        }
        return normalizedValue;
    }

    private String normalizeRequiredValue(String value, String blankMessage, String lengthMessage, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(blankMessage);
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new BusinessException(lengthMessage);
        }
        return normalizedValue;
    }

    private void ensureUniqueIdentity(String username, String email) {
        if (existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (existsByEmail(email)) {
            throw new BusinessException("邮箱已存在");
        }
    }

    private boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where username = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    private boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where email = ?",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    private UserAccount loadUserByUsernameAndEmail(String username, String email) {
        List<UserAccount> users = jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name, password_hash,
                       system_role, status, must_change_password
                from users
                where username = ? and email = ?
                order by id
                for update
                """,
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password")
                ),
                username,
                email
        );
        if (users.size() != 1) {
            throw new BusinessException("账号信息不匹配");
        }
        return users.getFirst();
    }

    private UserAccount loadUserForLogin(String loginId) {
        List<UserAccount> users = jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name, password_hash,
                       system_role, status, must_change_password
                from users
                where username = ? or email = ?
                order by id
                for update
                """,
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password")
                ),
                loginId,
                loginId
        );
        if (users.isEmpty()) {
            throw new BusinessException(INVALID_CREDENTIALS_MESSAGE);
        }
        ensureUniqueLoginMatch(users);
        return users.getFirst();
    }

    private UserAccount loadUserById(Long userId) {
        List<UserAccount> users = jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name, password_hash,
                       system_role, status, must_change_password
                from users
                where id = ?
                """,
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password")
                ),
                userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        return users.getFirst();
    }

    private void ensureUniqueLoginMatch(List<UserAccount> users) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (UserAccount user : users) {
            userIds.add(user.userId());
        }
        if (userIds.size() > 1) {
            throw new BusinessException("登录标识存在冲突，请联系管理员处理");
        }
    }

    private void ensureUserCanLogin(UserAccount user, String password) {
        if (user.status() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        if (user.passwordHash() == null || !passwordHasher.matches(password, user.passwordHash())) {
            throw new BusinessException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    private void ensureRefreshAllowed(UserAccount user) {
        if (user.status() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
    }

    private void updateSuccessfulLogin(Long userId) {
        jdbcTemplate.update(
                "update users set last_login_at = ?, updated_at = ? where id = ?",
                LocalDateTime.now(clock),
                LocalDateTime.now(clock),
                userId
        );
    }

    private String issueAccessToken(UserAccount user) {
        return jwtAccessTokenService.issueToken(
                new JwtAccessTokenService.TokenSubject(
                        user.userId(),
                        user.userCode(),
                        user.displayName(),
                        user.systemRole(),
                        user.mustChangePassword()
                )
        );
    }

    public record AuthTokens(
            Long userId,
            String accessToken,
            String refreshToken,
            boolean mustChangePassword
    ) {
    }

    private record LoginCommand(String loginId, String password) {
    }

    private record RegisterCommand(String username, String email, String displayName, String password) {
    }

    private record UserAccount(
            Long userId,
            String userCode,
            String username,
            String email,
            String displayName,
            String passwordHash,
            SystemRole systemRole,
            UserStatus status,
            boolean mustChangePassword
    ) {
    }
}
