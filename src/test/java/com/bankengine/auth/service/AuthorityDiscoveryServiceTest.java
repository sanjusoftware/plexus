package com.bankengine.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorityDiscoveryServiceTest {

    @RestController
    @PreAuthorize("hasAuthority('CLASS_LEVEL')")
    static class TestController {
        @PreAuthorize("hasAnyAuthority('METHOD_A', 'METHOD_B')")
        public void method1() {}

        @PreAuthorize("")
        public void methodEmpty() {}

        public void methodNoAnnotation() {}

        @PreAuthorize("hasAuthority('SINGLE_METHOD')")
        public void method2() {}
    }

    @RestController
    static class AnotherController {
        public void noPreAuth() {}
    }

    @Test
    void discoverAllAuthorities_ShouldFindAll() {
        ApplicationContext context = mock(ApplicationContext.class);
        TestController controller = new TestController();
        when(context.getBeansWithAnnotation(RestController.class)).thenReturn(Map.of("testController", controller));
        when(context.getBeansWithAnnotation(org.springframework.stereotype.Controller.class)).thenReturn(Map.of());

        AuthorityDiscoveryService service = new AuthorityDiscoveryService(context);
        Set<String> authorities = service.discoverAllAuthorities();

        assertTrue(authorities.contains("CLASS_LEVEL"));
        assertTrue(authorities.contains("METHOD_A"));
        assertTrue(authorities.contains("METHOD_B"));
    }
}
