package com.lemon.common;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 工具，用于 JSON 列（如 question.options）与对象互转。
 */
public final class JsonUtil {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonUtil() {
    }

    public static String toJson(Object obj) {
        return obj == null ? null : MAPPER.writeValueAsString(obj);
    }

    public static <T> T parse(String json, TypeReference<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, type);
    }
}
