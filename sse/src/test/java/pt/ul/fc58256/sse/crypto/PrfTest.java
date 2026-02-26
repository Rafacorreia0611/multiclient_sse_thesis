package pt.ul.fc58256.sse.crypto;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrfTest {

    private static KeyGenerator keyGenerator;

    @BeforeAll
    static void setup() {
        try {
            keyGenerator = KeyGenerator.getInstance("HmacSHA256");
            keyGenerator.init(256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        }
    }

    @Test
    @DisplayName("Same key and same input produce the same PRF output")
    void prfIsDeterministicForSameKeyAndInput() {
        SecretKey key = keyGenerator.generateKey();

        byte[] output1 = Prf.prf(key, "test input");
        byte[] output2 = Prf.prf(key, "test input");

        assertArrayEquals(output1, output2);
    }

    @Test
    @DisplayName("Same key and different inputs produce different PRF outputs")
    void prfChangesWhenInputChanges() {
        SecretKey key = keyGenerator.generateKey();

        byte[] output1 = Prf.prf(key, "input one");
        byte[] output2 = Prf.prf(key, "input two");

        assertFalse(Arrays.equals(output1, output2));
    }

    @Test
    @DisplayName("Different keys and same input produce different PRF outputs")
    void prfChangesWhenKeyChanges() {
        SecretKey key1 = keyGenerator.generateKey();
        SecretKey key2 = keyGenerator.generateKey();
        String input = "same input";

        byte[] output1 = Prf.prf(key1, input);
        byte[] output2 = Prf.prf(key2, input);

        assertFalse(Arrays.equals(output1, output2));
    }

    @Test
    @DisplayName("Token bytes converted to key produce the same output and vice versa")
    void prfIsEquivalentForSecretKeyAndTokenBytes() {
        SecretKey key = keyGenerator.generateKey();
        String token = "token-string1";
        String token2 = "token-string2";
        byte[] tokenBytes = Prf.prf(key, token);
        byte[] tokenBytes2 = Prf.prf(key, token2);

        byte[] output1 = Prf.prf(tokenBytes, "input one");
        byte[] output2 = Prf.prf(tokenBytes, "input one");
        byte[] output3 = Prf.prf(tokenBytes, "input two");
        byte[] output4 = Prf.prf(tokenBytes2, "input one");

        assertArrayEquals(output1, output2);
        assertFalse(Arrays.equals(output1, output3));
        assertFalse(Arrays.equals(output1, output4));
    }

    @Test
    @DisplayName("String and byte[] overloads are equivalent for the same content")
    void prfStringAndByteArrayOverloadsMatch() {
        SecretKey key = keyGenerator.generateKey();
        String input = "áccênted-input";

        byte[] fromString = Prf.prf(key, input);
        byte[] fromBytes = Prf.prf(key, input.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(fromString, fromBytes);
    }

    @Test
    @DisplayName("HMAC-SHA256 output length is always 32 bytes")
    void prfOutputLengthIs32Bytes() {
        SecretKey key = keyGenerator.generateKey();

        byte[] output = Prf.prf(key, "length-check");

        assertEquals(32, output.length);
    }
}
