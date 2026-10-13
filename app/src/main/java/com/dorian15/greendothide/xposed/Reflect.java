package com.dorian15.greendothide.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Minimal stand-in for the legacy {@code XposedHelpers}, which the modern API no longer ships.
 * Everything here targets plain application classes inside SystemUI, so plain reflection is
 * enough; no hidden-API exemption is involved.
 */
final class Reflect {

    private Reflect() {
    }

    static Class<?> findClass(String name, ClassLoader classLoader) throws ClassNotFoundException {
        return Class.forName(name, false, classLoader);
    }

    /** Looks the method up on {@code clazz} and every superclass, making it accessible. */
    static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "#" + name);
    }

    /** Looks the field up on {@code clazz} and every superclass, making it accessible. */
    static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchFieldException(clazz.getName() + "." + name);
    }

    static Object getField(Object instance, String name)
            throws NoSuchFieldException, IllegalAccessException {
        if (instance == null)
            throw new IllegalArgumentException("instance is null");
        return findField(instance.getClass(), name).get(instance);
    }

    static void setField(Object instance, String name, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        if (instance == null)
            throw new IllegalArgumentException("instance is null");
        findField(instance.getClass(), name).set(instance, value);
    }
}
