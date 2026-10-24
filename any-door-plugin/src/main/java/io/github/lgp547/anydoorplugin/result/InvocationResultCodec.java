package io.github.lgp547.anydoorplugin.result;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class InvocationResultCodec {
    private static final com.google.gson.Gson PRETTY = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private InvocationResultCodec() { }

    public static Decoded decodeAttach(String envelope) {
        JsonObject root = JsonParser.parseString(envelope).getAsJsonObject();
        if (!"success".equals(root.get("status").getAsString())) {
            return new Decoded(null, root.has("error") ? root.get("error").getAsString() : root.get("result").getAsString());
        }
        if (root.has("resultJson")) return new Decoded(pretty(root.get("resultJson")), null);
        JsonElement legacy = root.get("result");
        if (legacy == null || legacy.isJsonNull()) return new Decoded("null", null);
        String value = legacy.isJsonPrimitive() && legacy.getAsJsonPrimitive().isString() ? legacy.getAsString() : legacy.toString();
        try { return new Decoded(pretty(JsonParser.parseString(value)), null); }
        catch (Exception ignored) { return new Decoded(PRETTY.toJson(value), null); }
    }

    public static String prettyResponse(String response) {
        if (response == null || response.isBlank()) return "null";
        try { return pretty(JsonParser.parseString(response)); }
        catch (Exception ignored) { return PRETTY.toJson(response); }
    }

    public static String pretty(JsonElement value) { return PRETTY.toJson(value); }
    public record Decoded(String json, String error) { }
}
