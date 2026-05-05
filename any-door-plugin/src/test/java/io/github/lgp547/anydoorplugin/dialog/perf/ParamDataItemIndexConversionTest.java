package io.github.lgp547.anydoorplugin.dialog.perf;

import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParamDataItemIndexConversionTest {

    @Test
    void keepsIndexSearchFieldsAndSchemaCompatibleMetadata() {
        ParamDataItem item = new ParamDataItem();
        item.setId(42L);
        item.setUpdateTime(123456789L);
        item.setDeleted(0);
        item.setName("Save User");
        item.setQualifiedName("demo.UserService#save(java.lang.String)");
        item.setParam("{\"name\":\"alice\"}");

        ParamIndexData indexData = item.toIndexData();

        assertEquals(42L, indexData.getId());
        assertEquals("Save User", indexData.getName());
        assertEquals("demo.UserService#save(java.lang.String)", indexData.getQualifiedMethodName());
        assertEquals(123456789L, indexData.getUpdateTime());
        assertEquals(0, indexData.getDeleted());
    }
}
