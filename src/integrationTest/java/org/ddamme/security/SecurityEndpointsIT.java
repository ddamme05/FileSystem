package org.ddamme.security;

import org.ddamme.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityEndpointsIT extends BaseIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("Health endpoint is public")
    void public_health_is_accessible() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Info endpoint is public")
    void public_info_is_accessible() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Missing JWT gets 401 with WWW-Authenticate header")
    void missing_jwt_gets_401_with_www_authenticate() throws Exception {
        mvc.perform(get("/api/v1/files"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    @DisplayName("Invalid JWT gets 401")
    void invalid_jwt_gets_401() throws Exception {
        mvc.perform(get("/api/v1/files").header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
