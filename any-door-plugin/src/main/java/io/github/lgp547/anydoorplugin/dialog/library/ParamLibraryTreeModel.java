package io.github.lgp547.anydoorplugin.dialog.library;

import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class ParamLibraryTreeModel {

    private static final Comparator<String> TEXT_COMPARATOR = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private ParamLibraryTreeModel() {
    }

    public static Node build(List<ParamIndexData> source, String query) {
        String normalizedQuery = normalize(query);
        Map<String, Map<String, List<ParamIndexData>>> grouped = new TreeMap<>(TEXT_COMPARATOR);

        if (source != null) {
            for (ParamIndexData item : source) {
                MethodIdentity identity = MethodIdentity.parse(item == null ? null : item.getQualifiedMethodName());
                if (identity == null || !matches(item, identity, normalizedQuery)) {
                    continue;
                }
                grouped.computeIfAbsent(identity.className(), key -> new TreeMap<>(TEXT_COMPARATOR))
                        .computeIfAbsent(identity.methodSignature(), key -> new ArrayList<>())
                        .add(item);
            }
        }

        List<Node> classNodes = new ArrayList<>();
        grouped.forEach((className, methods) -> {
            List<Node> methodNodes = new ArrayList<>();
            methods.forEach((methodSignature, items) -> {
                items.sort(Comparator.comparing(ParamLibraryTreeModel::presetName, TEXT_COMPARATOR)
                        .thenComparing(item -> Objects.toString(item.getId(), "")));
                List<Node> presetNodes = items.stream()
                        .map(item -> Node.preset(className, item.getQualifiedMethodName(), item))
                        .toList();
                methodNodes.add(Node.method(className, methodSignature, presetNodes));
            });
            classNodes.add(Node.classNode(className, methodNodes));
        });
        return Node.root(classNodes);
    }

    private static boolean matches(ParamIndexData item, MethodIdentity identity, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return normalize(identity.className()).contains(query)
                || normalize(identity.methodSignature()).contains(query)
                || normalize(item.getName()).contains(query);
    }

    private static String presetName(ParamIndexData item) {
        String name = item == null ? null : item.getName();
        if (name == null || name.isBlank()) {
            return "未命名参数 #" + Objects.toString(item == null ? null : item.getId(), "");
        }
        return name;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
    }

    public enum NodeType {
        ROOT,
        CLASS,
        METHOD,
        PRESET
    }

    public static final class Node {
        private final NodeType type;
        private final String label;
        private final String secondaryText;
        private final String className;
        private final String qualifiedMethodName;
        private final ParamIndexData preset;
        private final List<Node> children;

        private Node(NodeType type,
                     String label,
                     String secondaryText,
                     String className,
                     String qualifiedMethodName,
                     ParamIndexData preset,
                     List<Node> children) {
            this.type = type;
            this.label = label;
            this.secondaryText = secondaryText;
            this.className = className;
            this.qualifiedMethodName = qualifiedMethodName;
            this.preset = preset;
            this.children = List.copyOf(children);
        }

        private static Node root(List<Node> children) {
            return new Node(NodeType.ROOT, "参数库", null, null, null, null, children);
        }

        private static Node classNode(String className, List<Node> children) {
            int separator = className.lastIndexOf('.');
            String label = separator < 0 ? className : className.substring(separator + 1);
            String packageName = separator < 0 ? "" : className.substring(0, separator);
            return new Node(NodeType.CLASS, label, packageName, className, null, null, children);
        }

        private static Node method(String className, String methodSignature, List<Node> children) {
            return new Node(NodeType.METHOD, methodSignature, null, className,
                    className + "#" + methodSignature, null, children);
        }

        private static Node preset(String className, String qualifiedMethodName, ParamIndexData preset) {
            return new Node(NodeType.PRESET, presetName(preset), null, className,
                    qualifiedMethodName, preset, List.of());
        }

        public NodeType getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

        public String getSecondaryText() {
            return secondaryText;
        }

        public String getClassName() {
            return className;
        }

        public String getQualifiedMethodName() {
            return qualifiedMethodName;
        }

        public ParamIndexData getPreset() {
            return preset;
        }

        public List<Node> getChildren() {
            return children;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record MethodIdentity(String className, String methodSignature) {
        private static MethodIdentity parse(String qualifiedMethodName) {
            if (qualifiedMethodName == null) {
                return null;
            }
            int separator = qualifiedMethodName.lastIndexOf('#');
            if (separator <= 0 || separator == qualifiedMethodName.length() - 1) {
                return null;
            }
            return new MethodIdentity(
                    qualifiedMethodName.substring(0, separator),
                    qualifiedMethodName.substring(separator + 1)
            );
        }
    }
}
