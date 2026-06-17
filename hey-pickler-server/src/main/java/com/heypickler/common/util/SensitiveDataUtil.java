package com.heypickler.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort JSON field masking for audit logs. Recursively walks a JSON tree and
 * replaces values of sensitive-named fields. Parsing failures return the raw input
 * unchanged — never let audit masking break the request being logged.
 */
@Slf4j
public final class SensitiveDataUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SensitiveDataUtil() {}

    public static String maskJson(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode masked = maskNode(root);
            return MAPPER.writeValueAsString(masked);
        } catch (Exception e) {
            // malformed JSON — return raw, truncation handled by caller
            return raw;
        }
    }

    private static JsonNode maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isValueNode() && isSensitive(name)) {
                    obj.put(name, maskValue(name, value.asText()));
                } else {
                    obj.set(name, maskNode(value));
                }
            });
            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, maskNode(arr.get(i)));
            }
            return arr;
        }
        return node;
    }

    private static boolean isSensitive(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.contains("password") || lower.contains("passwd")
                || lower.contains("token") || lower.contains("secret")
                || lower.contains("apikey") || lower.contains("accesstoken")
                || lower.contains("refreshtoken")
                || lower.contains("phone") || lower.contains("mobile")
                || lower.contains("idcard") || lower.contains("bankcard");
    }

    private static String maskValue(String fieldName, String value) {
        String lower = fieldName.toLowerCase();
        if (lower.contains("password") || lower.contains("passwd")
                || lower.contains("token") || lower.contains("secret")
                || lower.contains("apikey") || lower.contains("accesstoken")
                || lower.contains("refreshtoken")) {
            return "***";
        }
        if (lower.contains("phone") || lower.contains("mobile")) {
            return maskPhone(value);
        }
        if (lower.contains("idcard")) {
            return maskIdCard(value);
        }
        if (lower.contains("bankcard")) {
            return maskBankCard(value);
        }
        return "***";
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskIdCard(String id) {
        if (id == null || id.length() < 8) return "***";
        StringBuilder sb = new StringBuilder(id.substring(0, 4));
        for (int i = 0; i < id.length() - 6; i++) sb.append('*');
        sb.append(id.substring(id.length() - 2));
        return sb.toString();
    }

    private static String maskBankCard(String card) {
        if (card == null || card.length() < 4) return "***";
        return "****" + card.substring(card.length() - 4);
    }
}
