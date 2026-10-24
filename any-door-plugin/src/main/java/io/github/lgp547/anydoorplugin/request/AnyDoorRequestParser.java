package io.github.lgp547.anydoorplugin.request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnyDoorRequestParser {
    private static final Pattern RUN = Pattern.compile("(?m)^\\s*RUN\\s+([\\w.$]+)#([\\w$]+)(?:\\([^)]*\\))?\\s*$");
    private static final Pattern DIRECTIVE = Pattern.compile("(?m)^\\s*@([\\w.-]+)\\s*=\\s*(.*?)\\s*$");
    private static final Pattern NAME = Pattern.compile("(?m)^\\s*###(?:\\s*@name)?\\s*(.*)$");
    private AnyDoorRequestParser() { }
    public static AnyDoorRequest parse(String text) { return parseAll(text).get(0); }

    public static List<AnyDoorRequest> parseAll(String text) {
        String source = text == null ? "" : text;
        List<AnyDoorRequest> result = new ArrayList<>();
        Matcher separator = Pattern.compile("(?m)^\\s*###.*$").matcher(source);
        List<Integer> starts = new ArrayList<>();
        while (separator.find()) starts.add(separator.start());
        if (starts.isEmpty()) starts.add(0);
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index), end = index + 1 < starts.size() ? starts.get(index + 1) : source.length();
            String block = source.substring(start, end);
            Matcher run = RUN.matcher(block);
            if (!run.find()) continue;
            Map<String, String> directives = new LinkedHashMap<>();
            Matcher directive = DIRECTIVE.matcher(block);
            while (directive.find()) directives.put(directive.group(1), directive.group(2));
            Matcher name = NAME.matcher(block);
            String label = name.find() && !name.group(1).trim().isEmpty() ? name.group(1).trim() : run.group(2);
            int jsonStart = block.indexOf('{', run.end());
            String json = jsonStart < 0 ? "{}" : block.substring(jsonStart).trim();
            Long pid = number(directives.get("pid"));
            Integer timeout = numberInt(directives.get("timeout"));
            result.add(new AnyDoorRequest(label, pid, run.group(1), run.group(2), json, directives,
                    directives.getOrDefault("mode", "live"), timeout, "destructive".equalsIgnoreCase(directives.get("confirm")), start, end));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("缺少 RUN 包名.类名#方法名，例如 RUN demo.OrderService#createOrder");
        return result;
    }
    public static AnyDoorRequest parseAt(String text, int offset) {
        return parseAll(text).stream().filter(item -> offset >= item.startOffset() && offset <= item.endOffset()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("光标不在可执行请求块内"));
    }
    private static Long number(String value) { try { return value == null ? null : Long.valueOf(value); } catch (NumberFormatException e) { return null; } }
    private static Integer numberInt(String value) { try { return value == null ? 10 : Integer.valueOf(value.replaceAll("[^0-9]", "")); } catch (NumberFormatException e) { return 10; } }
}
