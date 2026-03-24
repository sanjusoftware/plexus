package com.bankengine.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping(value = { "/", "/login-view", "/dashboard", "/error", "/onboarding", "/auth/**",
            "/product-types", "/pricing-metadata", "/pricing-components", "/pricing-tiers", "/products", "/roles",
            "/{path:[^\\.]*}" })
    public String forward() {
        return "forward:/index.html";
    }
}
