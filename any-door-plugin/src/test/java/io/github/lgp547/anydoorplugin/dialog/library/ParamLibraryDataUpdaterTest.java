package io.github.lgp547.anydoorplugin.dialog.library;

import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamLibraryDataUpdaterTest {

    private static final String METHOD = "com.example.OrderService#submit()";

    @Test
    void renamesDataAndIndexTogether() {
        List<ParamDataItem> dataItems = new ArrayList<>(List.of(dataItem(1L, "旧名称")));
        List<ParamIndexData> indexItems = new ArrayList<>(List.of(indexItem(1L, "旧名称")));

        ParamMutationResult result = ParamLibraryDataUpdater.rename(
                dataItems, indexItems, METHOD, 1L, " 新名称 "
        );

        assertTrue(result.success());
        assertEquals("新名称", dataItems.get(0).getName());
        assertEquals("新名称", indexItems.get(0).getName());
    }

    @Test
    void leavesDataAndIndexUntouchedWhenRenameConflicts() {
        List<ParamDataItem> dataItems = new ArrayList<>(List.of(
                dataItem(1L, "方案一"),
                dataItem(2L, "方案二")
        ));
        List<ParamIndexData> indexItems = new ArrayList<>(List.of(
                indexItem(1L, "方案一"),
                indexItem(2L, "方案二")
        ));

        ParamMutationResult result = ParamLibraryDataUpdater.rename(
                dataItems, indexItems, METHOD, 1L, "方案二"
        );

        assertFalse(result.success());
        assertEquals("方案一", dataItems.get(0).getName());
        assertEquals("方案一", indexItems.get(0).getName());
    }

    @Test
    void removesMatchingDataAndIndexItems() {
        List<ParamDataItem> dataItems = new ArrayList<>(List.of(
                dataItem(1L, "方案一"),
                dataItem(2L, "方案二")
        ));
        List<ParamIndexData> indexItems = new ArrayList<>(List.of(
                indexItem(1L, "方案一"),
                indexItem(2L, "方案二")
        ));

        ParamLibraryDataUpdater.removeDataItems(dataItems, Set.of(1L));
        ParamLibraryDataUpdater.removeIndexItems(indexItems, Set.of(1L));

        assertEquals(List.of(2L), dataItems.stream().map(ParamDataItem::getId).toList());
        assertEquals(List.of(2L), indexItems.stream().map(ParamIndexData::getId).toList());
    }

    private static ParamDataItem dataItem(Long id, String name) {
        ParamDataItem item = new ParamDataItem(name, METHOD, "{}");
        item.setId(id);
        return item;
    }

    private static ParamIndexData indexItem(Long id, String name) {
        ParamIndexData item = new ParamIndexData();
        item.setId(id);
        item.setName(name);
        item.setQualifiedMethodName(METHOD);
        return item;
    }
}
