package com.heypickler.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataUtilTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void maskPassword_plainField() {
        String out = SensitiveDataUtil.maskJson("{\"password\":\"abc123\",\"name\":\"alice\"}");
        assertEquals("{\"password\":\"***\",\"name\":\"alice\"}", out);
    }

    @Test
    void maskPassword_caseInsensitive() {
        String out = SensitiveDataUtil.maskJson("{\"UserPassword\":\"abc\"}");
        assertEquals("{\"UserPassword\":\"***\"}", out);
    }

    @Test
    void maskToken_variants() {
        assertEquals("{\"token\":\"***\"}", SensitiveDataUtil.maskJson("{\"token\":\"xyz\"}"));
        assertEquals("{\"accessToken\":\"***\"}", SensitiveDataUtil.maskJson("{\"accessToken\":\"xyz\"}"));
        assertEquals("{\"refreshToken\":\"***\"}", SensitiveDataUtil.maskJson("{\"refreshToken\":\"xyz\"}"));
        assertEquals("{\"apiKey\":\"***\"}", SensitiveDataUtil.maskJson("{\"apiKey\":\"xyz\"}"));
        assertEquals("{\"secret\":\"***\"}", SensitiveDataUtil.maskJson("{\"secret\":\"xyz\"}"));
    }

    @Test
    void maskPhone_keepsFirst3Last4() {
        String out = SensitiveDataUtil.maskJson("{\"phone\":\"13812341234\"}");
        assertEquals("{\"phone\":\"138****1234\"}", out);
    }

    @Test
    void maskPhone_shortReturnsMasked() {
        assertEquals("{\"phone\":\"***\"}", SensitiveDataUtil.maskJson("{\"phone\":\"123\"}"));
    }

    @Test
    void nestedMap_recurses() {
        String out = SensitiveDataUtil.maskJson("{\"user\":{\"name\":\"a\",\"password\":\"p\",\"phone\":\"13800001111\"}}");
        assertTrue(out.contains("\"password\":\"***\""));
        assertTrue(out.contains("\"phone\":\"138****1111\""));
        assertTrue(out.contains("\"name\":\"a\""));
    }

    @Test
    void listOfMaps_recurses() {
        String out = SensitiveDataUtil.maskJson("[{\"password\":\"a\"},{\"password\":\"b\"}]");
        assertEquals("[{\"password\":\"***\"},{\"password\":\"***\"}]", out);
    }

    @Test
    void nonSensitiveUntouched() {
        String out = SensitiveDataUtil.maskJson("{\"title\":\"hello\",\"count\":42}");
        assertEquals("{\"title\":\"hello\",\"count\":42}", out);
    }

    @Test
    void malformedJsonReturnsTruncatedRaw() {
        String raw = "{not valid json";
        String out = SensitiveDataUtil.maskJson(raw);
        assertEquals(raw, out);
    }

    @Test
    void largePayload_truncatedInAspectNotUtil() {
        // util 不截断，截断是 aspect 的职责；这里只验证 util 能处理大输入
        StringBuilder sb = new StringBuilder("{\"data\":\"");
        for (int i = 0; i < 5000; i++) sb.append("x");
        sb.append("\"}");
        String out = SensitiveDataUtil.maskJson(sb.toString());
        assertTrue(out.contains("\"data\""));
    }
}
