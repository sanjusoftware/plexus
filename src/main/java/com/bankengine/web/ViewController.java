package com.bankengine.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping(value = { "/", "/login", "/dashboard", "/error", "/onboarding", "/auth/**",
            "/banks", "/banks/**", "/product-types", "/product-types/**", "/pricing-metadata", "/pricing-metadata/**",
            "/pricing-components/**", "/products/**", "/roles/**", "/{path:[^\\.]*}" })
    public String forward() {
        return "forward:/index.html";
    }
}
