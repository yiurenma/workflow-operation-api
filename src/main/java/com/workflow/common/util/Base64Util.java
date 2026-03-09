package com.workflow.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64Util {

    private Base64Util() {
    }

    public static String base64Decode(String encodeString, Boolean jsonIndicate, ObjectMapper objectMapper) {
        String decodeString = StringUtils.hasText(encodeString)
                ? new String(Base64.getDecoder().decode(encodeString), StandardCharsets.UTF_8)
                : encodeString;
        if (Boolean.TRUE.equals(jsonIndicate) && StringUtils.hasText(encodeString)) {
            try {
                return objectMapper.readTree(decodeString).toString();
            } catch (Exception e) {
                return decodeString;
            }
        }
        return decodeString;
    }

    public static String base64Encode(String string, Boolean jsonIndicate, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(string)) return string;
        if (Boolean.TRUE.equals(jsonIndicate)) {
            try {
                return Base64.getEncoder().encodeToString(objectMapper.readTree(string).toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8));
            }
        }
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8));
    }
}
