package com.project;

import com.project.annotation.WebSocketRoute;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class WebSocketAutoRegistrar {

    public static void scanAndRegister(String basePackage) {
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .forPackages(basePackage)
                        .addScanners(Scanners.MethodsAnnotated)
        );

        Set<Method> methods = reflections.getMethodsAnnotatedWith(WebSocketRoute.class);

        Set<Class<?>> controllerClasses = new HashSet<>();
        for (Method method : methods) {
            controllerClasses.add(method.getDeclaringClass());
        }

        for (Class<?> controllerClass : controllerClasses) {
            try {
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                WebSocketRouteRegistry.registerController(controllerInstance);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }
}
