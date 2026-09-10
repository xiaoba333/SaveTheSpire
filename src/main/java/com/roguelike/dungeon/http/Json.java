package com.roguelike.dungeon.http;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 JSON 工具：只覆盖 HTTP 层需要的那几种序列化/解析场景。
 *
 * <p>本项目 HTTP 层不引入 Jackson 等第三方依赖，字段名与取值完全由代码控制，
 * 手写序列化足够且可预期。</p>
 */
public final class Json {

    private Json() {
    }

    /**
     * 把字符串转成带引号的 JSON 字符串字面量；{@code null} 返回 {@code null} 字面量。
     */
    public static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"([^\"]*)\"\\s*:\\s*\"([^\"]*)\"");

    /**
     * 从 JSON 对象里提取某个 string 字段的取值；找不到返回 {@code null}。
     * 仅用于解析形如 {@code {"cardId":"..."}} 的简单请求体，不做完整 JSON 解析。
     */
    public static String field(String json, String key) {
        if (json == null) {
            return null;
        }
        Matcher m = STRING_FIELD.matcher(json);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                return m.group(2);
            }
        }
        return null;
    }
}
