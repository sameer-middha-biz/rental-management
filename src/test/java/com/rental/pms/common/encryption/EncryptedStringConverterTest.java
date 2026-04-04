package com.rental.pms.common.encryption;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    // Base64-encoded 32-byte key (decodes to "test-encryption-key-32-bytes!!!!")
    private static final String VALID_BASE64_KEY = "dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcyEhISE=";

    private final EncryptedStringConverter converter = new EncryptedStringConverter(VALID_BASE64_KEY);

    @Test
    void roundTrip_ShouldEncryptThenDecryptToOriginalPlaintext() {
        String plaintext = "my-secret-oauth-token-12345";

        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        String decrypted = converter.convertToEntityAttribute(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void roundTrip_WithUnicodeText_ShouldPreserveContent() {
        String plaintext = "Ünîcödé-tökën-✓-日本語";

        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        String decrypted = converter.convertToEntityAttribute(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void convertToDatabaseColumn_WithNull_ShouldReturnNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_WithNull_ShouldReturnNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_ShouldProduceDifferentCiphertextsForSamePlaintext() {
        String plaintext = "same-secret-value";

        String ciphertext1 = converter.convertToDatabaseColumn(plaintext);
        String ciphertext2 = converter.convertToDatabaseColumn(plaintext);

        // Different random IVs should produce different ciphertexts
        assertThat(ciphertext1).isNotEqualTo(ciphertext2);

        // But both should decrypt to the same value
        assertThat(converter.convertToEntityAttribute(ciphertext1)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(ciphertext2)).isEqualTo(plaintext);
    }

    @Test
    void constructor_WithInvalidBase64_ShouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new EncryptedStringConverter("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void constructor_WithWrongKeyLength_ShouldThrowIllegalArgument() {
        // Valid Base64 but decodes to only 16 bytes (not 32)
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new EncryptedStringConverter(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void convertToEntityAttribute_WithCorruptedCiphertext_ShouldThrowIllegalState() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("bm90LXZhbGlkLWNpcGhlcnRleHQ="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    @Test
    void convertToDatabaseColumn_ShouldProduceBase64EncodedOutput() {
        String ciphertext = converter.convertToDatabaseColumn("test-value");

        // Should be valid Base64 (no exception on decode)
        assertThat(ciphertext).matches("^[A-Za-z0-9+/]+=*$");
    }

    @Test
    void roundTrip_WithEmptyString_ShouldPreserveContent() {
        String plaintext = "";

        String ciphertext = converter.convertToDatabaseColumn(plaintext);
        String decrypted = converter.convertToEntityAttribute(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
    }
}
