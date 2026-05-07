package io.github.lgp547.anydoorplugin.dialog.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FieldSelectionResolver {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<FieldTarget> resolveFromJsonCaret(String rootParamName,
                                                      String declaredType,
                                                      String json,
                                                      int caretOffset) {
        if (json == null || json.isBlank() || caretOffset < 0 || caretOffset >= json.length()) {
            return Optional.empty();
        }
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(json);
        } catch (Exception e) {
            return Optional.empty();
        }
        List<PathRange> candidates = new ArrayList<PathRange>();
        collectCandidates(rootNode, "", json, 0, candidates);
        PathRange best = null;
        for (PathRange candidate : candidates) {
            if (candidate.contains(caretOffset)) {
                if (best == null || candidate.depth() >= best.depth()) {
                    best = candidate;
                }
            }
        }
        if (best == null) {
            best = findNearestCandidate(candidates, caretOffset);
        }
        if (best == null || best.path.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FieldTarget(
                rootParamName,
                best.path,
                best.path,
                declaredType,
                best.currentJsonValue,
                FieldTarget.TargetSource.CARET
        ));
    }

    private static PathRange findNearestCandidate(List<PathRange> candidates, int caretOffset) {
        PathRange best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (PathRange candidate : candidates) {
            int distance = candidate.distanceTo(caretOffset);
            if (distance < bestDistance || (distance == bestDistance && best != null && candidate.depth() > best.depth())) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return bestDistance <= 2 ? best : null;
    }

    private void collectCandidates(JsonNode node, String pathPrefix, String fullJson, int baseOffset, List<PathRange> out) {
        if (node == null || !node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String path = pathPrefix.isEmpty() ? field.getKey() : pathPrefix + "." + field.getKey();
            String fieldPattern = "\"" + field.getKey() + "\"";
            int keyIndex = fullJson.indexOf(fieldPattern);
            if (keyIndex < 0) {
                continue;
            }
            int colonIndex = fullJson.indexOf(':', keyIndex + fieldPattern.length());
            if (colonIndex < 0) {
                continue;
            }
            int valueStart = skipWhitespace(fullJson, colonIndex + 1);
            int valueEnd = findValueEnd(fullJson, valueStart);
            String currentJsonValue = valueStart >= 0 && valueEnd > valueStart ? fullJson.substring(valueStart, valueEnd) : "";
            out.add(new PathRange(path, baseOffset + keyIndex, baseOffset + valueEnd, currentJsonValue));
            if (field.getValue().isObject()) {
                collectCandidates(field.getValue(), path, currentJsonValue, baseOffset + valueStart, out);
            }
        }
    }

    private static int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int findValueEnd(String text, int start) {
        if (start < 0 || start >= text.length()) {
            return start;
        }
        char first = text.charAt(start);
        if (first == '"') {
            int index = start + 1;
            boolean escape = false;
            while (index < text.length()) {
                char ch = text.charAt(index);
                if (ch == '"' && !escape) {
                    return index + 1;
                }
                escape = ch == '\\' && !escape;
                if (ch != '\\') {
                    escape = false;
                }
                index++;
            }
            return text.length();
        }
        if (first == '{' || first == '[') {
            Deque<Character> stack = new ArrayDeque<Character>();
            stack.push(first);
            boolean inString = false;
            boolean escape = false;
            for (int index = start + 1; index < text.length(); index++) {
                char ch = text.charAt(index);
                if (ch == '"' && !escape) {
                    inString = !inString;
                }
                if (inString) {
                    escape = ch == '\\' && !escape;
                    if (ch != '\\') {
                        escape = false;
                    }
                    continue;
                }
                if (ch == '{' || ch == '[') {
                    stack.push(ch);
                } else if (ch == '}' || ch == ']') {
                    stack.pop();
                    if (stack.isEmpty()) {
                        return index + 1;
                    }
                }
            }
            return text.length();
        }
        int index = start;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch == ',' || ch == '}' || ch == ']') {
                return index;
            }
            index++;
        }
        return text.length();
    }

    private static final class PathRange {
        private final String path;
        private final int start;
        private final int end;
        private final String currentJsonValue;

        private PathRange(String path, int start, int end, String currentJsonValue) {
            this.path = path;
            this.start = start;
            this.end = end;
            this.currentJsonValue = currentJsonValue;
        }

        private boolean contains(int offset) {
            return offset >= start && offset <= end;
        }

        private int depth() {
            int depth = 1;
            for (int i = 0; i < path.length(); i++) {
                if (path.charAt(i) == '.') {
                    depth++;
                }
            }
            return depth;
        }

        private int distanceTo(int offset) {
            if (contains(offset)) {
                return 0;
            }
            if (offset < start) {
                return start - offset;
            }
            return offset - end;
        }
    }
}
