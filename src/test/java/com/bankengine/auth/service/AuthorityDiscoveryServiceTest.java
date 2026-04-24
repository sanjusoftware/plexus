package com.bankengine.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorityDiscoveryServiceTest {

    @RestController
    @RequestMapping("/test")
    @PreAuthorize("hasAuthority('CLASS_LEVEL')")
    static class TestController {
        @GetMapping("/m1")
        @PreAuthorize("hasAnyAuthority('METHOD_A', 'METHOD_B')")
        public void method1() {
        }

        @PostMapping("/m-empty")
        @PreAuthorize("")
        public void methodEmpty() {
        }

        @PutMapping("/m-none")
        public void methodNoAnnotation() {
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('SINGLE_METHOD')")
        public void method2(@PathVariable String id) {
        }

        @RequestMapping(path = "/m3", method = RequestMethod.PATCH)
        @PreAuthorize("hasAuthority('PATCH_AUTH')")
        public void method3() {
        }

        @RequestMapping(value = "/m4", method = {RequestMethod.GET, RequestMethod.POST})
        @PreAuthorize("hasAuthority('MULTI_METHOD')")
        public void method4() {
        }
    }

    @RestController
    static class AnotherController {
        public void noPreAuth() {
        }
    }

    @Controller
    @RequestMapping("/web")
    static class WebController {
        @GetMapping("/page")
        @PreAuthorize("hasAuthority('WEB_READ')")
        public String page() {
            return "page";
        }
    }

    @Test
    void discoverAllAuthorities_ShouldFindAll() {
        ApplicationContext context = mock(ApplicationContext.class);
        TestController controller = new TestController();
        WebController webController = new WebController();
        when(context.getBeansWithAnnotation(RestController.class)).thenReturn(Map.of("testController", controller));
        when(context.getBeansWithAnnotation(Controller.class)).thenReturn(Map.of("webController", webController));

        AuthorityDiscoveryService service = new AuthorityDiscoveryService(context);
        Set<String> authorities = service.discoverAllAuthorities();

        assertTrue(authorities.contains("CLASS_LEVEL"));
        assertTrue(authorities.contains("METHOD_A"));
        assertTrue(authorities.contains("METHOD_B"));
        assertTrue(authorities.contains("SINGLE_METHOD"));
        assertTrue(authorities.contains("PATCH_AUTH"));
        assertTrue(authorities.contains("MULTI_METHOD"));
        assertTrue(authorities.contains("WEB_READ"));
        assertFalse(authorities.contains(""));
    }

    @RestController
    @RequestMapping(value = {"/v1/test", "/v2/test"})
    static class MultiPathController {
        @RequestMapping(path = {"/m1", "/m2"}, method = {RequestMethod.GET, RequestMethod.POST})
        @PreAuthorize("hasAuthority('MULTI')")
        public void multi() {
        }

        @RequestMapping(value = "/slash/", method = RequestMethod.GET)
        @PreAuthorize("hasAuthority('SLASH')")
        public void slash() {
        }
    }

    @RestController
    static class NoMappingController {
        @RequestMapping
        @PreAuthorize("hasAuthority('NO_PATH')")
        public void noPath() {
        }
    }

    @Test
    void discoverEndpointPermissions_ShouldMapCorrectly() {
        ApplicationContext context = mock(ApplicationContext.class);
        TestController controller = new TestController();
        MultiPathController multiController = new MultiPathController();
        NoMappingController noMappingController = new NoMappingController();
        when(context.getBeansWithAnnotation(RestController.class)).thenReturn(Map.of(
                "testController", controller,
                "multiController", multiController,
                "noMappingController", noMappingController
        ));
        when(context.getBeansWithAnnotation(Controller.class)).thenReturn(Map.of());

        AuthorityDiscoveryService service = new AuthorityDiscoveryService(context);
        Map<String, Set<String>> permissions = service.discoverEndpointPermissions();

        // MultiPathController mappings
        assertTrue(permissions.containsKey("GET:/v1/test/m1"));
        assertTrue(permissions.containsKey("GET:/v1/test/m2"));
        assertTrue(permissions.containsKey("POST:/v1/test/m1"));
        assertTrue(permissions.containsKey("POST:/v2/test/m2"));
        // Test trailing slash removal in combinePaths
        assertFalse(permissions.containsKey("GET:/v1/test/slash/"));
        assertTrue(permissions.containsKey("GET:/v1/test/slash"));

        // method1: GET /test/m1 -> CLASS_LEVEL, METHOD_A, METHOD_B
        assertTrue(permissions.containsKey("GET:/test/m1"));
        Set<String> m1Auths = permissions.get("GET:/test/m1");
        assertTrue(m1Auths.contains("CLASS_LEVEL"));
        assertTrue(m1Auths.contains("METHOD_A"));
        assertTrue(m1Auths.contains("METHOD_B"));

        // method2: DELETE /test/* -> CLASS_LEVEL, SINGLE_METHOD
        assertTrue(permissions.containsKey("DELETE:/test/*"));
        Set<String> m2Auths = permissions.get("DELETE:/test/*");
        assertTrue(m2Auths.contains("CLASS_LEVEL"));
        assertTrue(m2Auths.contains("SINGLE_METHOD"));

        // method3: PATCH /test/m3 -> CLASS_LEVEL, PATCH_AUTH
        assertTrue(permissions.containsKey("PATCH:/test/m3"));

        // method4: GET /test/m4 and POST /test/m4 -> CLASS_LEVEL, MULTI_METHOD
        assertTrue(permissions.containsKey("GET:/test/m4"));
        assertTrue(permissions.containsKey("POST:/test/m4"));

        // methodNoAnnotation: PUT /test/m-none -> should only have CLASS_LEVEL
        assertTrue(permissions.containsKey("PUT:/test/m-none"));
        assertEquals(Set.of("CLASS_LEVEL"), permissions.get("PUT:/test/m-none"));
    }
}
