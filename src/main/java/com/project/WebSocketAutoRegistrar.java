package com.project;

import com.project.annotation.Command;
import com.project.annotation.WebSocketRoute;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Method;
import java.util.Set;

@Slf4j
public class WebSocketAutoRegistrar {

    public static void scanAndRegister(String basePackage) {
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .forPackages(basePackage)
                        .addScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated)
        );

        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(WebSocketRoute.class);

        for (Class<?> controllerClass : controllerClasses) {
            try {
                Object controllerInstance = DIContainer.createOrGetInstance(controllerClass);
                WebSocketRouteRegistry.registerController(controllerInstance);
            } catch (Exception e) {
                log.error("Failed to register controller {}: {}", controllerClass.getName(), e.getMessage());
            }
        }

        Set<Method> commandMethods = reflections.getMethodsAnnotatedWith(Command.class);
        for (Method method : commandMethods) {
            if (!method.getDeclaringClass().isAnnotationPresent(WebSocketRoute.class)) {
                log.error("Method {} is annotated with @Command but its controller is not annotated with @WebSocketRoute",
                        method.getName());
            }
        }
    }
}


