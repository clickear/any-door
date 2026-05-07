package io.github.lgp547.anydoorplugin.dialog.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldExpressionCacheTest {

    @Test
    void storesExpressionsPerMethodAndPath() {
        FieldExpressionCache cache = new FieldExpressionCache();
        cache.put(new FieldExpressionCacheKey("demo.Service#doRun", "user.address.city"), "bean(UserService.class).city()");
        cache.put(new FieldExpressionCacheKey("demo.Service#doRun", "user.name"), "\"Alice\"");

        assertEquals("bean(UserService.class).city()", cache.get(new FieldExpressionCacheKey("demo.Service#doRun", "user.address.city")).orElseThrow());
        assertEquals("\"Alice\"", cache.get(new FieldExpressionCacheKey("demo.Service#doRun", "user.name")).orElseThrow());
    }
}
