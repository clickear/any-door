package io.github.lgp547.anydoorplugin.dialog.perf;

import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ParamIndexSearchEngine {

    private volatile List<SearchEntry> entries = List.of();

    public void rebuild(List<ParamIndexData> source) {
        List<SearchEntry> next = new ArrayList<>();
        if (source != null) {
            for (ParamIndexData item : source) {
                next.add(new SearchEntry(item));
            }
        }
        entries = List.copyOf(next);
    }

    public List<ParamIndexData> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalized = keyword.toLowerCase(Locale.ROOT);
        List<ParamIndexData> result = new ArrayList<>();
        for (SearchEntry entry : entries) {
            if (entry.matches(normalized)) {
                result.add(entry.source());
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private record SearchEntry(ParamIndexData source, String lowerName, String lowerQualifiedMethodName) {
        private SearchEntry(ParamIndexData source) {
            this(
                    source,
                    normalize(source == null ? null : source.getName()),
                    normalize(source == null ? null : source.getQualifiedMethodName())
            );
        }

        private boolean matches(String keyword) {
            return lowerName.contains(keyword) || lowerQualifiedMethodName.contains(keyword);
        }

        private static String normalize(String value) {
            return Objects.toString(value, "").toLowerCase(Locale.ROOT);
        }
    }
}
