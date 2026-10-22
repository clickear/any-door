package io.github.lgp547.anydoorplugin.dialog.library;

import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;
import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ParamLibraryDataUpdater {

    private ParamLibraryDataUpdater() {
    }

    public static ParamMutationResult rename(List<ParamDataItem> dataItems,
                                             List<ParamIndexData> indexItems,
                                             String qualifiedMethodName,
                                             Long id,
                                             String newName) {
        ParamMutationResult validation = ParamPresetRules.validateName(
                dataItems, qualifiedMethodName, id, newName
        );
        if (!validation.success()) {
            return validation;
        }

        ParamDataItem target = dataItems.stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return ParamMutationResult.failure("参数方案不存在或已被删除");
        }

        String normalizedName = ParamPresetRules.normalizeName(newName);
        target.setName(normalizedName);
        indexItems.stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .ifPresent(item -> item.setName(normalizedName));
        return ParamMutationResult.ok();
    }

    public static void removeDataItems(List<ParamDataItem> dataItems, Collection<Long> ids) {
        if (dataItems == null || ids == null || ids.isEmpty()) {
            return;
        }
        dataItems.removeIf(item -> ids.contains(item.getId()));
    }

    public static void removeIndexItems(List<ParamIndexData> indexItems, Set<Long> ids) {
        if (indexItems == null || ids == null || ids.isEmpty()) {
            return;
        }
        indexItems.removeIf(item -> ids.contains(item.getId()));
    }
}
