package io.github.lgp547.anydoorplugin.data.domain;

import java.util.ArrayList;
import java.util.List;

/** Project-local navigation container. Presets stay owned by their method data file. */
public class ParamCollection {
    private String id;
    private String name;
    private List<String> methodNames = new ArrayList<>();

    public ParamCollection() { }
    public ParamCollection(String id, String name) { this.id = id; this.name = name; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getMethodNames() { return methodNames; }
    public void setMethodNames(List<String> methodNames) { this.methodNames = methodNames == null ? new ArrayList<>() : methodNames; }
}
