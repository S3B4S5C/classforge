package com.classforge.shared.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** SPA entry points only; API and missing asset paths must retain their HTTP semantics. */
@Controller
@ConditionalOnProperty(name = "classforge.web.spa.enabled", havingValue = "true")
public class SpaController {
    @GetMapping({"/", "/login", "/register", "/projects", "/projects/{projectId}",
            "/projects/{projectId}/assistant", "/projects/{projectId}/activity"})
    public String index() { return "forward:/index.html"; }
}
