package com.bankengine.auth.service;

import org.springframework.aop.support.AopUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AuthorityDiscoveryService {

    private final ApplicationContext applicationContext;
    // Regex: captures multiple occurrences and handles 'hasAnyAuthority'
    private static final Pattern AUTHORITY_PATTERN = Pattern.compile("has(?:Any)?Authority\\s*\\(\\s*['\"](.+?)['\"]\\s*\\)");

    public AuthorityDiscoveryService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Cacheable(value = "systemAuthorities", key = "'masterList'")
    public Set<String> discoverAllAuthorities() {
        // 1. Get all controller beans
        Set<Object> beans = Stream.concat(
            applicationContext.getBeansWithAnnotation(RestController.class).values().stream(),
            applicationContext.getBeansWithAnnotation(Controller.class).values().stream()
        ).collect(Collectors.toSet());

        return beans.stream()
            .flatMap(bean -> {
                Class<?> targetClass = AopUtils.getTargetClass(bean);

                // 2. Extract from Class level AND Method level
                Stream<String> classAuths = extractFromElement(targetClass);
                Stream<String> methodAuths = Arrays.stream(targetClass.getDeclaredMethods())
                                                   .flatMap(this::extractFromElement);

                return Stream.concat(classAuths, methodAuths);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Cacheable(value = "systemAuthorities", key = "'permissionsMap'")
    public Map<String, Set<String>> discoverEndpointPermissions() {
        Map<String, Set<String>> endpointMap = new HashMap<>();

        Set<Object> beans = Stream.concat(
            applicationContext.getBeansWithAnnotation(RestController.class).values().stream(),
            applicationContext.getBeansWithAnnotation(Controller.class).values().stream()
        ).collect(Collectors.toSet());

        for (Object bean : beans) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            String[] classPaths = extractPaths(targetClass);
            Set<String> classAuthorities = extractFromElement(targetClass).collect(Collectors.toSet());

            for (Method method : targetClass.getDeclaredMethods()) {
                String[] methodPaths = extractPaths(method);
                RequestMethod[] routingMethods = extractMethods(method);

                if (methodPaths.length == 0 && routingMethods.length == 0) continue;

                Set<String> methodAuthorities = extractFromElement(method).collect(Collectors.toSet());
                Set<String> totalAuthorities = new HashSet<>(classAuthorities);
                totalAuthorities.addAll(methodAuthorities);

                if (totalAuthorities.isEmpty()) continue;

                for (String classPath : classPaths) {
                    for (String methodPath : methodPaths) {
                        String fullPath = combinePaths(classPath, methodPath);
                        for (RequestMethod routingMethod : routingMethods) {
                            String key = routingMethod.name() + ":" + fullPath;
                            endpointMap.put(key, totalAuthorities);
                        }
                    }
                }
            }
        }
        return endpointMap;
    }

    private String[] extractPaths(AnnotatedElement element) {
        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class);
        if (requestMapping != null && requestMapping.path().length > 0) {
            return requestMapping.path();
        }
        if (requestMapping != null && requestMapping.value().length > 0) {
            return requestMapping.value();
        }
        return new String[]{""};
    }

    private RequestMethod[] extractMethods(Method method) {
        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (requestMapping != null && requestMapping.method().length > 0) {
            return requestMapping.method();
        }
        return new RequestMethod[0];
    }

    private String combinePaths(String classPath, String methodPath) {
        String path = (classPath + "/" + methodPath).replaceAll("//+", "/");
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        // Normalize {pathVariable} to * for consistent frontend matching
        return path.replaceAll("\\{[^}]+\\}", "*");
    }

    /**
     * Extracts the permission string from the @PreAuthorize expression.
     */
    private Stream<String> extractFromElement(AnnotatedElement element) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(element, PreAuthorize.class);
        if (annotation == null || annotation.value().isEmpty()) {
            return Stream.empty();
        }

        // Use Matcher.results() (Java 9+) to find ALL matches in one string
        Matcher matcher = AUTHORITY_PATTERN.matcher(annotation.value());
        return matcher.results()
            .map(matchResult -> matchResult.group(1))
            // Handle comma-separated lists inside hasAnyAuthority('A', 'B')
            .flatMap(s -> Arrays.stream(s.split("['\"]\\s*,\\s*['\"]")));
    }
}
