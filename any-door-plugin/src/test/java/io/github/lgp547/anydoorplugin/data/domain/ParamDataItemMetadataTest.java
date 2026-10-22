package io.github.lgp547.anydoorplugin.data.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamDataItemMetadataTest {

    @Test
    void copiesFavoriteAndLastUsedMetadataToTheLibraryIndex() {
        ParamDataItem item = new ParamDataItem("normal order", "demo.OrderService#createOrder()", "{}");
        item.setId(42L);
        item.setFavorite(true);
        item.setLastUsedAt(1_726_000_000_000L);

        ParamIndexData index = item.toIndexData();

        assertEquals(42L, index.getId());
        assertTrue(index.getFavorite());
        assertEquals(1_726_000_000_000L, index.getLastUsedAt());
    }
}
