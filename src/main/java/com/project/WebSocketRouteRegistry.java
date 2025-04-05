package com.project;

import com.project.annotation.WebSocketRoute;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class WebSocketRouteRegistry {
    private static final Map<String, RouteHandler> handlers = new HashMap<>();
    private static final Map<Method, Object> controllerInstances = new HashMap<>();

    public static void registerController(Object controller) {
        for (Method method : controller.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(WebSocketRoute.class)) {
                WebSocketRoute annotation = method.getAnnotation(WebSocketRoute.class);
                Class<?> paramType = method.getParameterTypes()[0];
                boolean isBinary = paramType == byte[].class;

                handlers.put(annotation.value(), new RouteHandler(method, isBinary));
                controllerInstances.put(method, controller);
            }
        }
    }

    public static RouteHandler getHandler(String route) {
        return handlers.get(route);
    }

    public static Object getControllerInstance(Method method) {
        return controllerInstances.get(method);
    }

    public static class RouteHandler {
        private final Method method;
        private final boolean isBinary;

        public RouteHandler(Method method, boolean isBinary) {
            this.method = method;
            this.isBinary = isBinary;
        }

        public Method getMethod() {
            return method;
        }

        public boolean isBinary() {
            return isBinary;
        }
    }
}