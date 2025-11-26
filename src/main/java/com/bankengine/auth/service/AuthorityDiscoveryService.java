package com.bankengine.auth.service;

import org.springframework.aop.support.AopUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AuthorityDiscoveryService {

    private final ApplicationContext applicationContext;
    private static final Pattern AUTHORITY_PATTERN = Pattern.compile("hasAuthority\\(['\"](.+?)['\"]\\)");

    public AuthorityDiscoveryService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Scans all beans marked as @Controller or @RestController and extracts
     * all unique authority strings used in @PreAuthorize annotations.
     * * The result is cached after the first execution for the application's lifetime,
     * avoiding repeated reflection overhead.
     */
    @Cacheable(value = "systemAuthorities", key = "'masterList'")
    public Set<String> discoverAllAuthorities() {
        System.out.println("--- Executing Authority Discovery (Reflection Scan) ---");

        // 1. Get all beans annotated with @Controller or @RestController
        Stream<Object> controllerBeans = Stream.concat(
            applicationContext.getBeansWithAnnotation(RestController.class).values().stream(),
            applicationContext.getBeansWithAnnotation(Controller.class).values().stream()
        ).distinct();

        // 2. Process the beans, handling potential Spring proxies
        Set<String> authorities = controllerBeans
            .flatMap(bean -> {
                // Get the target class, bypassing any Spring proxies (CGLIB)
                Class<?> targetClass = AopUtils.getTargetClass(bean);
                return java.util.Arrays.stream(targetClass.getDeclaredMethods());
            })

            // 3. Filter for methods that have the @PreAuthorize annotation
            .filter(method -> method.isAnnotationPresent(PreAuthorize.class))

            // 4. Extract the authority string
            .map(this::extractAuthorityFromAnnotation)

            // 5. Collect unique, non-null authorities
            .filter(authority -> authority != null && !authority.isEmpty())
            .collect(Collectors.toSet());

        System.out.println("--- Authority Discovery Complete. Found " + authorities.size() + " authorities. ---");
        return authorities;
    }

    /**
     * Extracts the permission string from the @PreAuthorize expression.
     */
    private String extractAuthorityFromAnnotation(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        if (annotation == null) {
            return null;
        }

        String expression = annotation.value();
        Matcher matcher = AUTHORITY_PATTERN.matcher(expression);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}