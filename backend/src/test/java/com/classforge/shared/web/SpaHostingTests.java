package com.classforge.shared.web;

import com.classforge.auth.security.JwtAuthenticationFilter;
import com.classforge.auth.security.JwtService;
import com.classforge.auth.security.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the real security chain and MVC routes without MongoDB or provider calls. */
class SpaHostingTests {
    AnnotationConfigWebApplicationContext context;

    MockMvc app(boolean enabled) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "classforge.web.spa.enabled=" + enabled);
        context.register(WebConfig.class);
        context.scan("com.classforge.shared.web");
        context.refresh();
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { if (context != null) context.close(); }

    @Test void azurePublicRoutesForwardToAngularIncludingDeepLinks() throws Exception {
        var mvc = app(true);
        for (String path : new String[]{"/", "/login", "/register", "/projects",
                "/projects/27000000-0000-0000-0000-000000000100",
                "/projects/27000000-0000-0000-0000-000000000100/assistant",
                "/projects/27000000-0000-0000-0000-000000000100/activity"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test void publicIndexAndHashedAssetsAreServedWithoutJwt() throws Exception {
        var mvc = app(true);
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("spa-hosting-fixture")));
        mvc.perform(get("/main-test.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fixtureLoaded")));
    }

    @Test void apiAndUnknownApiRoutesNeverFallBackToPublicHtml() throws Exception {
        var mvc = app(true);
        for (String path : new String[]{"/api/projects", "/api/not-found", "/api/projects/example/assistant/messages"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(content().contentType("application/json"));
        }
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test void postToSpaIsNotPublicAndMissingAssetIsNotHtml() throws Exception {
        var mvc = app(true);
        mvc.perform(post("/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/missing-file.js")).andExpect(status().isNotFound());
    }

    @Test void localAndAwsModesDoNotExposeSpaRoutesOrAssets() throws Exception {
        var mvc = app(false);
        for (String path : new String[]{"/", "/login", "/projects", "/index.html", "/main-test.js"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
    static class WebConfig implements WebMvcConfigurer {
        @Bean JwtService jwtService() { return new JwtService("isolated-spa-hosting-test-secret-2026", 60); }
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/spa-hosting-fixture/");
        }
    }
}
