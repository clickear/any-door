package io.github.lgp547.anydoor.common.util;

import java.lang.reflect.InvocationTargetException;

/** Calls framework interfaces in their own loader without linking to the agent's framework copy. */
public final class AnyDoorReflectionUtil {
    private AnyDoorReflectionUtil() { }

    public static Class<?> findType(Class<?> type, String name) {
        if (type == null) return null;
        if (name.equals(type.getName())) return type;
        for (Class<?> parent : type.getInterfaces()) {
            Class<?> found = findType(parent, name);
            if (found != null) return found;
        }
        return findType(type.getSuperclass(), name);
    }

    public static boolean isInstance(Object object, String type) {
        return object != null && findType(object.getClass(), type) != null;
    }

    public static Object call(Object object, String contract, String method, Class<?>[] parameters, Object... args) {
        Class<?> type = findType(object.getClass(), contract);
        if (type == null) throw new IllegalArgumentException("Object does not implement " + contract);
        try {
            return type.getMethod(method, parameters).invoke(object, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Failed to call " + contract + "." + method, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call " + contract + "." + method, e);
        }
    }

    public static Object call(Object object, String contract, String method) {
        return call(object, contract, method, new Class<?>[0]);
    }
}
