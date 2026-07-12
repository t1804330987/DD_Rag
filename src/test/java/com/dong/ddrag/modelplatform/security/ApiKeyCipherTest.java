package com.dong.ddrag.modelplatform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiKeyCipherTest {

    private final ApiKeyCipher cipher = new ApiKeyCipher("unit-test-master-secret");

    @Test
    void shouldEncryptAndDecryptRoundTrip() {
        String plain = "sk-sensitive-1234";
        String encrypted = cipher.encrypt(plain);

        assertThat(encrypted).startsWith(ApiKeyCipher.PREFIX);
        assertThat(encrypted).doesNotContain(plain);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        String first = cipher.encrypt("same-key");
        String second = cipher.encrypt("same-key");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("same-key");
        assertThat(cipher.decrypt(second)).isEqualTo("same-key");
    }

    @Test
    void shouldPassThroughLegacyPlaintextOnDecrypt() {
        assertThat(cipher.decrypt("sk-legacy-plaintext")).isEqualTo("sk-legacy-plaintext");
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void shouldNotDoubleEncrypt() {
        String encrypted = cipher.encrypt("once");
        assertThat(cipher.encrypt(encrypted)).isEqualTo(encrypted);
    }

    @Test
    void shouldFailWhenMasterSecretDiffers() {
        String encrypted = cipher.encrypt("secret");
        ApiKeyCipher other = new ApiKeyCipher("different-master-secret");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }
}
