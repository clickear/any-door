package io.github.lgp547.anydoorplugin.dialog.expression;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FieldExpressionCache {
    private final Map<FieldExpressionCacheKey, String> cache = new ConcurrentHashMap<>();

    public void put(FieldExpressionCacheKey key, String expression) {
        cache.put(key, expression);
    }

    public Optional<String> get(FieldExpressionCacheKey key) {
        return Optional.ofNullable(cache.get(key));
    }
}
