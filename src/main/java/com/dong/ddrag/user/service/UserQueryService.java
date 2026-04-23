package com.dong.ddrag.user.service;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.user.model.vo.AdminUserItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserQueryService {

    private final JdbcTemplate jdbcTemplate;

    public UserQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminUserItemResponse> listUsers() {
        return jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name,
                       system_role, status, must_change_password, last_login_at
                from users
                order by id
                """,
                (resultSet, rowNum) -> new AdminUserItemResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password"),
                        resultSet.getTimestamp("last_login_at") == null
                                ? null
                                : resultSet.getTimestamp("last_login_at").toLocalDateTime()
                )
        );
    }

    public AdminUserItemResponse getUser(Long userId) {
        List<AdminUserItemResponse> users = jdbcTemplate.query(
                """
                select id, user_code, username, email, display_name,
                       system_role, status, must_change_password, last_login_at
                from users
                where id = ?
                """,
                (resultSet, rowNum) -> new AdminUserItemResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password"),
                        resultSet.getTimestamp("last_login_at") == null
                                ? null
                                : resultSet.getTimestamp("last_login_at").toLocalDateTime()
                ),
                userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("用户不存在");
        }
        return users.getFirst();
    }

    public boolean existsByUsername(String username) {
        return count("select count(*) from users where username = ?", username) > 0;
    }

    public boolean existsByEmail(String email) {
        return count("select count(*) from users where email = ?", email) > 0;
    }

    private long count(String sql, String value) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, value);
        return count == null ? 0 : count;
    }
}
