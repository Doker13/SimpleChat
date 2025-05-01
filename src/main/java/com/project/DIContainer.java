package com.project;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DIContainer {
    private static final Map<Class<?>, Object> singletonInstances = new HashMap<>();

    public static <T> void registerSingleton(Class<T> clazz, T instance) {
        singletonInstances.put(clazz, instance);
    }

    public static <T> T getSingleton(Class<T> clazz) {
        return clazz.cast(singletonInstances.get(clazz));
    }

    public static Object createOrGetInstance(Class<?> clazz) {
        if (singletonInstances.containsKey(clazz)) {
            return singletonInstances.get(clazz);
        }

        try {
            Constructor<?>[] constructors = clazz.getConstructors();
            if (constructors.length == 0) {
                throw new RuntimeException("No public constructor found for " + clazz.getName());
            }

            Constructor<?> constructor = constructors[0];
            Object[] params = Arrays.stream(constructor.getParameterTypes())
                    .map(DIContainer::createOrGetInstance)
                    .toArray();

            Object instance = constructor.newInstance(params);
            singletonInstances.put(clazz, instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + clazz.getName(), e);
        }
    }
}
