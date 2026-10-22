package io.github.lgp547.anydoorplugin.data.domain;

/**
 * @description:
 * @author: zhouh
 * @date: 2023-07-22 11:29
 **/
public class ParamIndexData extends DataItem {

    private String name;

    private String qualifiedMethodName;

    private Boolean favorite = false;

    private Long lastUsedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQualifiedMethodName() {
        return qualifiedMethodName;
    }

    public void setQualifiedMethodName(String qualifiedMethodName) {
        this.qualifiedMethodName = qualifiedMethodName;
    }

    public Boolean getFavorite() { return favorite; }

    public void setFavorite(Boolean favorite) { this.favorite = favorite; }

    public Long getLastUsedAt() { return lastUsedAt; }

    public void setLastUsedAt(Long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
