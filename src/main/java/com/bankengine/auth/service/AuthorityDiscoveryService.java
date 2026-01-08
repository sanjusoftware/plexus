package com.bankengine.auth.service;

import org.springframework.aop.support.AopUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Extracts the permission string from the @PreAuthorize expression.
     */
    private Stream<String> extractFromElement(AnnotatedElement element) {
        PreAuthorize annotation = element.getAnnotation(PreAuthorize.class);
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