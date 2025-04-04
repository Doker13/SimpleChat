package com.project;

import com.project.annotation.FileRoute;
import com.project.annotation.JSONRoute;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class WebSocketRouteRegistry {
    private static final Map<String, Method> jsonHandlers = new HashMap<>();
    private static final Map<String, Method> fileHandlers = new HashMap<>();
    private static final Map<Method, Object> controllerInstances = new HashMap<>();

    public static void registerController(Object controller) {
        for (Method method : controller.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(JSONRoute.class)) {
                JSONRoute annotation = method.getAnnotation(JSONRoute.class);
                jsonHandlers.put(annotation.value(), method);
                controllerInstances.put(method, controller);
            } else if (method.isAnnotationPresent(FileRoute.class)) {
                FileRoute annotation = method.getAnnotation(FileRoute.class);
                fileHandlers.put(annotation.value(), method);
                controllerInstances.put(method, controller);
            }
        }
    }

    public static Method getJsonHandler(String route) {
        return jsonHandlers.get(route);
    }

    public static Method getFileHandler(String route) {
        return fileHandlers.get(route);
    }

    public static Object getControllerInstance(Method method) {
        return controllerInstances.get(method);
    }
}