package com.project;

import com.project.annotation.Binary;
import com.project.annotation.Command;
import com.project.annotation.WebSocketRoute;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class WebSocketRouteRegistry {
    private static final Map<String, Map<String, RouteHandler>> routeHandlers = new HashMap<>();
    private static final Map<Method, Object> controllerInstances = new HashMap<>();

    public static void registerController(Object controller) {
        Class<?> controllerClass = controller.getClass();
        if (!controllerClass.isAnnotationPresent(WebSocketRoute.class)) {
            throw new IllegalArgumentException("Controller must be annotated with @WebSocketRoute");
        }

        String route = controllerClass.getAnnotation(WebSocketRoute.class).route();

        for (Method method : controllerClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Command.class)) {
                Command commandAnnotation = method.getAnnotation(Command.class);
                String command = commandAnnotation.value();

                boolean isBinary = method.isAnnotationPresent(Binary.class);
                validateMethodParameters(method, isBinary);

                RouteHandler handler = new RouteHandler(method, isBinary);

                routeHandlers.computeIfAbsent(route, k -> new HashMap<>())
                        .put(command, handler);
                controllerInstances.put(method, controller);
            }
        }
    }

    private static void validateMethodParameters(Method method, boolean isBinary) {
        Class<?>[] paramTypes = method.getParameterTypes();

        if (isBinary) {
            if (paramTypes.length != 2) {
                throw new IllegalArgumentException(
                        "@Binary method must have exactly 2 parameters: Object and byte[]");
            }
            if (paramTypes[1] != byte[].class) {
                throw new IllegalArgumentException(
                        "Second parameter of @Binary method must be byte[]");
            }
        } else {
            if (paramTypes.length != 1) {
                throw new IllegalArgumentException(
                        "@Command method must have exactly 1 parameter");
            }
        }
    }

    public static RouteHandler getHandler(String route, String command) {
        Map<String, RouteHandler> commandHandlers = routeHandlers.get(route);
        if (commandHandlers == null) {
            return null;
        }
        return commandHandlers.get(command);
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