package com.workflow.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base64UtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void base64DecodeShouldReturnOriginalWhenInputEmpty() {
        assertEquals("", Base64Util.base64Decode("", true, objectMapper));
        assertEquals(null, Base64Util.base64Decode(null, true, objectMapper));
    }

    @Test
    void base64DecodeShouldNormalizeJsonWhenJsonFlagIsTrue() {
        String encoded = Base64.getEncoder().encodeToString("{\"b\":2,\"a\":1}".getBytes(StandardCharsets.UTF_8));
        assertEquals("{\"b\":2,\"a\":1}", Base64Util.base64Decode(encoded, true, objectMapper));
    }

    @Test
    void base64DecodeShouldReturnDecodedTextWhenJsonIsInvalid() {
        String encoded = Base64.getEncoder().encodeToString("not-json".getBytes(StandardCharsets.UTF_8));
        assertEquals("not-json", Base64Util.base64Decode(encoded, true, objectMapper));
    }

    @Test
    void base64DecodeShouldThrowWhenEncodedValueIsInvalidBase64() {
        assertThrows(IllegalArgumentException.class, () -> Base64Util.base64Decode("%%%invalid%%%", true, objectMapper));
    }

    @Test
    void base64EncodeShouldReturnOriginalWhenInputEmpty() {
        assertEquals("", Base64Util.base64Encode("", true, objectMapper));
        assertEquals(null, Base64Util.base64Encode(null, true, objectMapper));
    }

    @Test
    void base64EncodeShouldNormalizeJsonWhenJsonFlagIsTrue() {
        String encoded = Base64Util.base64Encode("{\"b\":2,\"a\":1}", true, objectMapper);
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("{\"b\":2,\"a\":1}", decoded);
    }

    @Test
    void base64EncodeShouldFallbackToRawStringWhenJsonIsInvalid() {
        String encoded = Base64Util.base64Encode("not-json", true, objectMapper);
        assertEquals("not-json", new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    @Test
    void base64EncodeShouldEncodeRawStringWhenJsonFlagIsFalse() {
        String encoded = Base64Util.base64Encode("hello", false, objectMapper);
        assertEquals("hello", new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }
}
