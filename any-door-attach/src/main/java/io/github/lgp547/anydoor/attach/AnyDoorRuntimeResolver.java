package io.github.lgp547.anydoor.attach;

import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.common.util.AnyDoorBeanResolver;
import io.github.lgp547.anydoor.common.util.AnyDoorClassUtil;
import io.github.lgp547.anydoor.common.util.AnyDoorReflectionUtil;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A per-request view of application types and framework objects in their defining loaders. */
public final class AnyDoorRuntimeResolver {
    private static final String CONTEXT = "org.springframework.context.ApplicationContext";
    private static final String FACTORY = "org.springframework.beans.factory.BeanFactory";
    private static final String CONFIGURABLE_FACTORY = "org.springframework.beans.factory.config.ConfigurableBeanFactory";
    private final Class<?>[] loaded;
    private final Object[] contexts;
    private final Object[] factories;
    private final String initializationWarning;

    public AnyDoorRuntimeResolver(Instrumentation instrumentation) {
        this(instrumentation, false);
    }

    public AnyDoorRuntimeResolver(Instrumentation instrumentation, boolean tolerant) {
        this.loaded = instrumentation.getAllLoadedClasses();
        Object[] discoveredContexts = instances(CONTEXT);
        Object[] selectedContexts;
        String warning = null;
        try { selectedContexts = filterContexts(discoveredContexts); }
        catch (Throwable error) {
            if (!tolerant) throw error instanceof RuntimeException ? (RuntimeException) error : new IllegalStateException(error);
            selectedContexts = discoveredContexts;
            warning = error.getClass().getName() + ": " + error.getMessage();
        }
        this.contexts = selectedContexts;
        String contextName = System.getProperty("anydoor.spring.core.context.name");
        this.factories = contextName == null || contextName.isEmpty() ? instances(FACTORY) : new Object[0];
        this.initializationWarning = warning;
    }

    AnyDoorRuntimeResolver(Class<?>[] loaded, Object[] contexts, Object[] factories) {
        this.loaded = loaded;
        this.contexts = filterContexts(contexts);
        this.factories = factories;
        this.initializationWarning = null;
    }

    public String getInitializationWarning() { return initializationWarning; }

    private Object[] instances(String name) {
        Set<Object> result = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (Class<?> type : loaded) {
            if (name.equals(type.getName())) Collections.addAll(result, AnyDoorVmToolUtils.getInstances(type));
        }
        return result.toArray();
    }

    private static Object[] filterContexts(Object[] contexts) {
        String name = System.getProperty("anydoor.spring.core.context.name");
        if (name == null || name.isEmpty()) return contexts;
        List<Object> matches = new ArrayList<>();
        for (Object context : contexts) {
            String displayName = (String) AnyDoorReflectionUtil.call(context, CONTEXT, "getDisplayName");
            if (displayName.contains(name)) matches.add(context);
        }
        if (matches.isEmpty()) throw new IllegalArgumentException("not found context name: " + name);
        return matches.toArray();
    }

    public List<Map<String, String>> listBeans(AnyDoorRunDto request) {
        List<Class<?>> types = findTypes(request.getClassName(), request.getClassLoaderId());
        List<Map<String, String>> result = new ArrayList<>();
        for (Class<?> type : types) result.addAll(AnyDoorBeanResolver.list(type, contexts, factories));
        if (result.isEmpty() && types.size() > 1) throw ambiguousType(types);
        return result;
    }

    public Target resolve(AnyDoorRunDto request) {
        List<Class<?>> types = findTypes(request.getClassName(), request.getClassLoaderId());
        AnyDoorBeanResolver.ResolvedBean selected = AnyDoorBeanResolver.resolve(types, request.getBeanName(),
                request.getBeanContextId(), contexts, factories);
        if (selected != null) return new Target(selected.type, selected.bean);
        if (types.size() != 1) throw ambiguousType(types);
        Class<?> type = types.get(0);
        return new Target(type, AnyDoorVmToolUtils.getOrGenInstance(type));
    }

    public List<Class<?>> findTypes(String name, String loaderId) {
        Set<Class<?>> types = new LinkedHashSet<>();
        Set<ClassLoader> applicationLoaders = Collections.newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());
        for (Class<?> type : loaded) {
            if (name.equals(type.getName()) && matches(type, loaderId)) types.add(type);
        }
        // A lazy Spring bean's class need not have been loaded when the snapshot was taken.
        for (Object context : contexts) {
            applicationLoaders.add((ClassLoader) AnyDoorReflectionUtil.call(context, CONTEXT, "getClassLoader"));
        }
        for (Object factory : factories) {
            if (AnyDoorReflectionUtil.isInstance(factory, CONFIGURABLE_FACTORY)) {
                applicationLoaders.add((ClassLoader) AnyDoorReflectionUtil.call(factory, CONFIGURABLE_FACTORY, "getBeanClassLoader"));
            }
        }
        for (ClassLoader loader : applicationLoaders) addVisibleType(types, name, loaderId, loader);
        if (types.isEmpty()) {
            Set<ClassLoader> loaders = Collections.newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());
            for (Class<?> type : loaded) loaders.add(type.getClassLoader());
            loaders.add(Thread.currentThread().getContextClassLoader());
            for (ClassLoader loader : loaders) {
                if (loaderId != null && !loaderId.isEmpty() && !loaderId.equals(AnyDoorClassUtil.classLoaderId(loader))) continue;
                if (loader != null && loader.getClass().getName().equals("io.github.lgp547.anydoor.common.util.AnyDoorClassloader")) continue;
                addVisibleType(types, name, loaderId, loader);
            }
        }
        if (types.isEmpty()) throw new IllegalArgumentException("Application class not found: " + name + "; classLoaderId=" + loaderId);
        return new ArrayList<>(types);
    }

    private static void addVisibleType(Set<Class<?>> types, String name, String loaderId, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            if (matches(type, loaderId)) types.add(type);
        } catch (ClassNotFoundException ignored) { }
    }

    private static boolean matches(Class<?> type, String loaderId) {
        return loaderId == null || loaderId.isEmpty() || loaderId.equals(AnyDoorClassUtil.classLoaderId(type.getClassLoader()));
    }

    private static IllegalArgumentException ambiguousType(List<Class<?>> types) {
        List<String> loaders = new ArrayList<>();
        for (Class<?> type : types) loaders.add(AnyDoorClassUtil.classLoaderId(type.getClassLoader()));
        return new IllegalArgumentException("Multiple application classes named " + types.get(0).getName()
                + "; specify classLoaderId: " + loaders);
    }

    public static final class Target {
        public final Class<?> type;
        public final Object bean;
        private Target(Class<?> type, Object bean) { this.type = type; this.bean = bean; }
    }
}
