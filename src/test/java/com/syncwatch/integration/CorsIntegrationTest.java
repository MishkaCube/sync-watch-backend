package com.syncwatch.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses MockMvc because the JDK HTTP client silently drops the restricted Origin header,
 * which would make a CORS test pass or fail for the wrong reason.
 */
@SpringBootTest(properties = {"rezka.service.enabled=false", "rezka.feature.enabled=false"})
@AutoConfigureMockMvc
class CorsIntegrationTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("the dev frontend origin passes the preflight on every API controller")
    void preflightAllowsFrontendOrigin() throws Exception {
        for (String path : new String[] {"/api/rooms", "/api/files/upload", "/api/health", "/api/config", "/api/hls/manifest"}) {
            mvc.perform(options(path)
                            .header(HttpHeaders.ORIGIN, FRONTEND)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
        }
    }

    @Test
    @DisplayName("other origins are rejected in the preflight")
    void preflightRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/rooms")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a real request from the frontend origin gets the CORS header back")
    void actualRequestGetsAllowOrigin() throws Exception {
        mvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
    }
}
