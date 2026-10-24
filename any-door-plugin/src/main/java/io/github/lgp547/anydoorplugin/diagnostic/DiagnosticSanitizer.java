package io.github.lgp547.anydoorplugin.diagnostic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class DiagnosticSanitizer {
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwd|token|secret|authorization|api[_-]?key)(\\s*[=:]\\s*)([^\\s,;]+)");
    private static final Pattern UNIX_HOME = Pattern.compile("/(home|Users)/[^/\\s]+/");
    private static final Pattern WINDOWS_HOME = Pattern.compile("(?i)[A-Z]:\\\\Users\\\\[^\\\\\\s]+\\\\");
    private DiagnosticSanitizer() { }
    public static String text(String value) {
        if (value == null) return null;
        String result = SECRET.matcher(value).replaceAll("$1$2<redacted>");
        result = UNIX_HOME.matcher(result).replaceAll("/<user>/");
        result = WINDOWS_HOME.matcher(result).replaceAll("C:\\\\<user>\\\\");
        return result.length() > 40000 ? result.substring(0, 40000) + "\n… diagnostic text truncated" : result;
    }
    public static String exception(Throwable error) {
        if (error == null) return null;
        StringWriter writer = new StringWriter(); error.printStackTrace(new PrintWriter(writer)); return text(writer.toString());
    }
    public static String exception(Throwable error, JsonObject request) {
        String result = exception(error);
        if (request == null || !request.has("content")) return result;
        List<String> values = new ArrayList<>();
        try {
            JsonElement content = request.get("content");
            if (content.isJsonPrimitive() && content.getAsJsonPrimitive().isString()) content = JsonParser.parseString(content.getAsString());
            collect(content, values);
        } catch (Throwable ignored) { }
        values.sort(Comparator.comparingInt(String::length).reversed());
        for (String value : values) if (value.length() >= 3) result = result.replace(value, "<parameter>");
        return result;
    }
    private static void collect(JsonElement value, List<String> output) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) { output.add(value.getAsString()); return; }
        if (value.isJsonArray()) for (JsonElement item : value.getAsJsonArray()) collect(item, output);
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) collect(entry.getValue(), output);
    }
}
