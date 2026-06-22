package com.heypickler.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AesUtilTest {

    private AesUtil newAesUtil(String key) {
        AesUtil util = new AesUtil();
        ReflectionTestUtils.setField(util, "key", key);
        return util;
    }

    @Test
    void validate_acceptsKeyOf16Bytes() {
        AesUtil util = newAesUtil("0123456789abcdef");
        assertDoesNotThrow(util::validate);
    }

    @Test
    void validate_acceptsKeyOf24Bytes() {
        AesUtil util = newAesUtil("0123456789abcdefghijklmn");
        assertDoesNotThrow(util::validate);
    }

    @Test
    void validate_acceptsKeyOf32Bytes() {
        AesUtil util = newAesUtil("0123456789abcdefghijklmnopqrstuv");
        assertDoesNotThrow(util::validate);
    }

    @Test
    void validate_rejectsKeyShorterThan16Bytes() {
        AesUtil util = newAesUtil("abc");
        IllegalStateException ex = assertThrows(IllegalStateException.class, util::validate);
        assertTrue(ex.getMessage().contains("16, 24, or 32 bytes"), "error message should mention valid lengths");
        assertTrue(ex.getMessage().contains("openssl rand"), "error message should recommend generation command");
    }

    @Test
    void validate_rejectsKeyBetweenValidLengths() {
        AesUtil util = newAesUtil("0123456789abcdef0"); // 17 bytes
        IllegalStateException ex = assertThrows(IllegalStateException.class, util::validate);
        assertTrue(ex.getMessage().contains("Got 17 bytes"), "error message should report actual length");
    }

    @Test
    void encryptDecrypt_roundTripsFor16ByteKey() {
        AesUtil util = newAesUtil("0123456789abcdef");
        String plain = "13800138000";
        String cipher = util.encrypt(plain);
        assertNotEquals(plain, cipher);
        assertEquals(plain, util.decrypt(cipher));
    }

    @Test
    void encryptDecrypt_roundTripsFor32ByteKey() {
        AesUtil util = newAesUtil("0123456789abcdefghijklmnopqrstuv");
        String plain = "user@example.com";
        String cipher = util.encrypt(plain);
        assertEquals(plain, util.decrypt(cipher));
    }

    @Test
    void encrypt_producesDifferentCiphersForSameInput() {
        // GCM uses random IV — same plaintext must produce different ciphertexts
        AesUtil util = newAesUtil("0123456789abcdef");
        String plain = "13800138000";
        assertNotEquals(util.encrypt(plain), util.encrypt(plain));
    }
}
