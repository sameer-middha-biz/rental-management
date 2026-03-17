package com.rental.pms.common.encryption;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private static final String VALID_32_BYTE_KEY = "test-encryption-key-32-bytes!!!!";

    private final EncryptedStringConverter converter = new EncryptedStringConverter(VALID_32_BYTE_KEY);

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
    void constructor_WithInvalidKeyLength_ShouldThrowIllegalArgument() {
        assertThatThrownBy(() -> new EncryptedStringConverter("too-short-key"))
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
