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
    void maskEmail_keepsDomainAndFirstTwoLocal() {
        String out = SensitiveDataUtil.maskJson("{\"email\":\"alice@example.com\"}");
        assertEquals("{\"email\":\"al***@example.com\"}", out);
    }

    @Test
    void maskEmail_shortLocal_returnsMasked() {
        String out = SensitiveDataUtil.maskJson("{\"email\":\"a@example.com\"}");
        assertEquals("{\"email\":\"**@example.com\"}", out);
    }

    @Test
    void maskCardNo_variants() {
        // bankCard 字段名 → maskBankCard；card_no 走相同分支
        assertEquals("{\"bankCard\":\"****1234\"}", SensitiveDataUtil.maskJson("{\"bankCard\":\"6222600012341234\"}"));
        assertEquals("{\"card_no\":\"****5678\"}", SensitiveDataUtil.maskJson("{\"card_no\":\"6222600012345678\"}"));
        assertEquals("{\"cardNo\":\"****9012\"}", SensitiveDataUtil.maskJson("{\"cardNo\":\"6222600012349012\"}"));
    }

    @Test
    void maskAddress_keepsFirst2Last2() {
        // "北京市朝阳区建国路88号" (length=12) → first 2 "北京" + *** + last 2 "8号"
        String out = SensitiveDataUtil.maskJson("{\"address\":\"北京市朝阳区建国路88号\"}");
        assertEquals("{\"address\":\"北京***8号\"}", out);
    }

    @Test
    void maskTel_usesPhoneLikeFormat() {
        String out = SensitiveDataUtil.maskJson("{\"tel\":\"010-12345678\"}");
        // tel 不强制 11 位，按 phone 规则（>7 chars 保留前后）
        assertTrue(out.contains("\"tel\":\"010****5678\""), "actual=" + out);
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
