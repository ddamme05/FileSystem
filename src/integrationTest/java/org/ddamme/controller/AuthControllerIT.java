package org.ddamme.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddamme.dto.RegisterRequest;
import org.ddamme.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.ddamme.testsupport.BaseIntegrationTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("register returns 400 for invalid payload in full context")
    void register_invalidPayload_returnsBadRequest() throws Exception {
        RegisterRequest payload = RegisterRequest.builder()
                .username("")
                .email("bad")
                .password("short")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register successfully creates a new user")
    void register_success() throws Exception {
        String uniqueUser = "ituser-register-" + System.currentTimeMillis();
        RegisterRequest register = RegisterRequest.builder()
                .username(uniqueUser)
                .email(uniqueUser + "@example.com")
                .password("a-valid-password-123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.username").value(uniqueUser));
    }

    @Test
    @DisplayName("login succeeds and returns token")
    void login_success_returnsToken() throws Exception {
        String uniqueUser = "ituser-login-" + System.currentTimeMillis();
        String email = uniqueUser + "@example.com";

        // Register user first
        RegisterRequest register = RegisterRequest.builder()
                .username(uniqueUser)
                .email(email)
                .password("secret123")
                .build();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        // Then login
        LoginRequest login = LoginRequest.builder()
                .username(uniqueUser)
                .password("secret123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.username").value(uniqueUser))
                .andExpect(jsonPath("$.role").isString());
    }
}


