package io.github.lgp547.anydoor.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves named Spring beans without falling back to an unrelated instance. */
public final class AnyDoorBeanResolver {
    private static final String CONTEXT = "org.springframework.context.ApplicationContext";
    private static final String CONFIGURABLE_CONTEXT = "org.springframework.context.ConfigurableApplicationContext";
    private static final String FACTORY = "org.springframework.beans.factory.BeanFactory";
    private static final String LISTABLE_FACTORY = "org.springframework.beans.factory.ListableBeanFactory";
    private static final String CONFIGURABLE_FACTORY = "org.springframework.beans.factory.config.ConfigurableListableBeanFactory";
    private static final String HIERARCHICAL_FACTORY = "org.springframework.beans.factory.HierarchicalBeanFactory";
    private AnyDoorBeanResolver() { }

    public static List<Map<String, String>> list(Class<?> type, Object[] contexts, Object[] factories) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Candidate candidate : candidates(type, contexts, factories)) result.add(candidate.describe());
        return result;
    }

    /** Returns null only when no Spring bean exists and no explicit selection was supplied. */
    public static Object resolve(Class<?> type, String beanName, String contextId,
                                 Object[] contexts, Object[] factories) {
        ResolvedBean resolved = resolve(Collections.<Class<?>>singletonList(type), beanName, contextId, contexts, factories);
        return resolved == null ? null : resolved.bean;
    }

    /** Resolves one bean across several definitions of the same binary class name. */
    public static ResolvedBean resolve(List<Class<?>> types, String beanName, String contextId,
                                       Object[] contexts, Object[] factories) {
        boolean selected = hasText(beanName);
        if (!selected && hasText(contextId)) {
            throw new IllegalArgumentException("beanName is required when beanContextId is specified");
        }
        List<Candidate> matches = new ArrayList<>();
        for (Class<?> type : types) {
            for (Candidate candidate : candidates(type, contexts, factories)) {
                if (selected && !candidate.name.equals(beanName) && !candidate.hasAlias(beanName)) continue;
                if (hasText(contextId) && !candidate.contextId.equals(contextId)) continue;
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            if (selected) throw new IllegalArgumentException("Spring bean not found: " + beanName + " for " + types);
            return null;
        }
        if (matches.size() > 1) {
            List<String> names = new ArrayList<>();
            for (Candidate candidate : matches) names.add(candidate.name + " [" + candidate.contextId + ", loader "
                    + AnyDoorClassUtil.classLoaderId(candidate.requestedType.getClassLoader()) + "]");
            throw new IllegalArgumentException("Multiple Spring beans for " + types
                    + "; select beanName and beanContextId: " + names);
        }
        Candidate selectedBean = matches.get(0);
        Class<?> type = selectedBean.requestedType;
        Object bean = AnyDoorReflectionUtil.call(selectedBean.factory, FACTORY, "getBean", new Class<?>[]{String.class}, selectedBean.name);
        if (!type.isInstance(bean) && !type.isAssignableFrom(AnyDoorAopUtil.getTargetClass(bean))) {
            throw new IllegalArgumentException("Selected Spring bean is no longer of type " + type.getName());
        }
        return new ResolvedBean(type, bean);
    }

    public static final class ResolvedBean {
        public final Class<?> type;
        public final Object bean;
        private ResolvedBean(Class<?> type, Object bean) { this.type = type; this.bean = bean; }
    }

    private static boolean hasText(String text) { return text != null && !text.trim().isEmpty(); }

    private static List<Candidate> candidates(Class<?> type, Object[] contexts, Object[] factories) {
        List<Candidate> result = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        if (contexts != null) {
            for (Object context : contexts) {
                if (context == null) continue;
                String name = (String) AnyDoorReflectionUtil.call(context, CONTEXT, "getDisplayName");
                collect(type, context, name, visited, result);
            }
        }
        if (factories != null) {
            for (Object factory : factories) collect(type, factory, "BeanFactory", visited, result);
        }
        result.sort(Comparator.comparing((Candidate item) -> item.contextName)
                .thenComparing(item -> item.name).thenComparing(item -> item.contextId));
        return result;
    }

    private static void collect(Class<?> type, Object factory, String contextName,
                                Set<Object> visited, List<Candidate> result) {
        // Native BeanFactory discovery also returns ApplicationContext wrappers.
        // Normalize them before deduplication, including parent-context traversal.
        if (AnyDoorReflectionUtil.isInstance(factory, CONFIGURABLE_CONTEXT)) {
            if (!(Boolean) AnyDoorReflectionUtil.call(factory, CONFIGURABLE_CONTEXT, "isActive")) return;
            contextName = (String) AnyDoorReflectionUtil.call(factory, CONTEXT, "getDisplayName");
            factory = AnyDoorReflectionUtil.call(factory, CONFIGURABLE_CONTEXT, "getBeanFactory");
        }
        if (factory == null || !visited.add(factory)) return;
        if (AnyDoorReflectionUtil.isInstance(factory, LISTABLE_FACTORY)) {
            Set<String> names = new LinkedHashSet<>();
            Collections.addAll(names, (String[]) AnyDoorReflectionUtil.call(factory, LISTABLE_FACTORY, "getBeanNamesForType",
                    new Class<?>[]{Class.class, boolean.class, boolean.class}, type, true, false));
            // A JDK proxy is not assignable to its concrete implementation class. Inspect
            // existing singletons' target types without creating unrelated beans.
            if (AnyDoorReflectionUtil.isInstance(factory, CONFIGURABLE_FACTORY)) {
                for (String name : (String[]) AnyDoorReflectionUtil.call(factory, CONFIGURABLE_FACTORY, "getSingletonNames")) {
                    Object singleton = singleton(factory, name);
                    if (singleton != null && type.isAssignableFrom(AnyDoorAopUtil.getTargetClass(singleton))) names.add(name);
                }
            }
            for (String name : names) {
                Object bean = AnyDoorReflectionUtil.isInstance(factory, CONFIGURABLE_FACTORY) ? singleton(factory, name) : null;
                if (bean != null && !type.isInstance(bean) && !type.isAssignableFrom(AnyDoorAopUtil.getTargetClass(bean))) {
                    bean = null; // The cached singleton may be a FactoryBean, not its product.
                }
                result.add(new Candidate(factory, name, contextName, bean, type));
            }
        }
        if (AnyDoorReflectionUtil.isInstance(factory, HIERARCHICAL_FACTORY)) {
            collect(type, AnyDoorReflectionUtil.call(factory, HIERARCHICAL_FACTORY, "getParentBeanFactory"), contextName + " (parent)", visited, result);
        }
    }

    private static Object singleton(Object factory, String name) {
        return AnyDoorReflectionUtil.call(factory, CONFIGURABLE_FACTORY, "getSingleton", new Class<?>[]{String.class}, name);
    }

    private static final class Candidate {
        private final Object factory;
        private final String name;
        private final String contextId;
        private final String contextName;
        private final Object bean;
        private final Class<?> requestedType;

        private Candidate(Object factory, String name, String contextName, Object bean, Class<?> requestedType) {
            this.factory = factory;
            this.name = name;
            this.contextId = Integer.toHexString(System.identityHashCode(factory));
            this.contextName = contextName;
            this.bean = bean;
            this.requestedType = requestedType;
        }

        private boolean hasAlias(String alias) {
            for (String item : (String[]) AnyDoorReflectionUtil.call(factory, FACTORY, "getAliases", new Class<?>[]{String.class}, name)) {
                if (item.equals(alias)) return true;
            }
            return false;
        }

        private Map<String, String> describe() {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("beanName", name);
            metadata.put("beanContextId", contextId);
            metadata.put("contextName", contextName);
            Class<?> type = bean == null ? (Class<?>) AnyDoorReflectionUtil.call(factory, FACTORY, "getType",
                    new Class<?>[]{String.class, boolean.class}, name, false) : AnyDoorAopUtil.getTargetClass(bean);
            metadata.put("className", type == null ? "unknown" : type.getName());
            metadata.put("classLoaderId", AnyDoorClassUtil.classLoaderId(requestedType.getClassLoader()));
            metadata.put("proxyType", bean == null ? "未实例化" : AnyDoorAopUtil.isCglibProxy(bean) ? "CGLIB"
                    : AnyDoorAopUtil.isAopProxy(bean) ? "JDK" : "none");
            return metadata;
        }
    }
}
