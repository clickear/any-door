package io.github.lgp547.anydoorplugin.dialog.library;

import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamPresetRulesTest {

    private static final String METHOD = "com.example.OrderService#submit(java.lang.String)";

    @Test
    void rejectsBlankAndDuplicateNamesInsideSameMethod() {
        ParamDataItem existing = item(1L, "正常订单", METHOD);

        assertFalse(ParamPresetRules.validateName(List.of(existing), METHOD, null, " ").success());
        assertFalse(ParamPresetRules.validateName(List.of(existing), METHOD, null, " 正常订单 ").success());
    }

    @Test
    void allowsSameNameForAnotherMethodAndCurrentRecord() {
        ParamDataItem existing = item(1L, "正常订单", METHOD);

        assertTrue(ParamPresetRules.validateName(
                List.of(existing), "com.example.OrderService#cancel()", null, "正常订单"
        ).success());
        assertTrue(ParamPresetRules.validateName(List.of(existing), METHOD, 1L, "正常订单").success());
    }

    @Test
    void normalizesStoredName() {
        assertEquals("正常订单", ParamPresetRules.normalizeName("  正常订单  "));
    }

    private static ParamDataItem item(Long id, String name, String qualifiedMethodName) {
        ParamDataItem item = new ParamDataItem(name, qualifiedMethodName, "{}");
        item.setId(id);
        return item;
    }
}
