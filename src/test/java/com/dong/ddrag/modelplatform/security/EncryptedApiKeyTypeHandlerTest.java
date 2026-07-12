package com.dong.ddrag.modelplatform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptedApiKeyTypeHandlerTest {

    private final ApiKeyCipher cipher = new ApiKeyCipher("type-handler-test-secret");
    private final EncryptedApiKeyTypeHandler handler = new EncryptedApiKeyTypeHandler();

    @BeforeEach
    void setUp() {
        ApiKeyCipherHolder.set(cipher);
    }

    @AfterEach
    void tearDown() {
        ApiKeyCipherHolder.clear();
    }

    @Test
    void shouldEncryptOnWriteAndDecryptOnRead() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, "sk-write-me", JdbcType.VARCHAR);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ps).setString(eq(1), captor.capture());
        assertThat(captor.getValue()).startsWith(ApiKeyCipher.PREFIX).doesNotContain("sk-write-me");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("api_key_plaintext")).thenReturn(captor.getValue());
        assertThat(handler.getNullableResult(rs, "api_key_plaintext")).isEqualTo("sk-write-me");
    }

    @Test
    void shouldReadLegacyPlaintextUnchanged() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("api_key_plaintext")).thenReturn("sk-legacy");
        assertThat(handler.getNullableResult(rs, "api_key_plaintext")).isEqualTo("sk-legacy");
    }
}
