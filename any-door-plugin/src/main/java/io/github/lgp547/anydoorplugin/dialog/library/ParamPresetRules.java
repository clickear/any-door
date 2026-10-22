package io.github.lgp547.anydoorplugin.dialog.library;

import io.github.lgp547.anydoorplugin.data.domain.ParamDataItem;

import java.util.Collection;
import java.util.Objects;

public final class ParamPresetRules {

    private ParamPresetRules() {
    }

    public static ParamMutationResult validateName(Collection<ParamDataItem> items,
                                                   String qualifiedMethodName,
                                                   Long currentId,
                                                   String candidateName) {
        String normalizedName = normalizeName(candidateName);
        if (normalizedName.isEmpty()) {
            return ParamMutationResult.failure("参数方案名称不能为空");
        }
        if (items != null) {
            boolean duplicate = items.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(item -> Objects.equals(item.getQualifiedName(), qualifiedMethodName)
                            && !Objects.equals(item.getId(), currentId)
                            && normalizedName.equalsIgnoreCase(normalizeName(item.getName())));
            if (duplicate) {
                return ParamMutationResult.failure("同一方法下已存在同名参数方案");
            }
        }
        return ParamMutationResult.ok();
    }

    public static String normalizeName(String name) {
        return Objects.toString(name, "").trim();
    }
}
