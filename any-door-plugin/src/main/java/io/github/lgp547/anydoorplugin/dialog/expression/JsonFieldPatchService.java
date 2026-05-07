package io.github.lgp547.anydoorplugin.dialog.expression;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonFieldPatchService {
    private final Gson gson = new Gson();

    public String patch(String fullJson, String path, String valueJson) {
        JsonObject root = JsonParser.parseString(fullJson).getAsJsonObject();
        JsonElement replacement = JsonParser.parseString(valueJson);
        String[] parts = path.split("\\.");
        JsonObject cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            cursor = cursor.getAsJsonObject(parts[i]);
        }
        cursor.add(parts[parts.length - 1], replacement);
        return gson.toJson(root);
    }
}
