package org.ddamme.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.ddamme.dto.LoginRequest;
import org.ddamme.dto.RegisterRequest;
import org.ddamme.testsupport.BaseIntegrationTest;
import org.ddamme.testsupport.TestStorageConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestStorageConfig.class)
class FileControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("/api/v1/files is 401 without JWT")
    void files_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/files")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/v1/files/upload requires multipart and auth (401)")
    void upload_requiresAuth() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());
        mockMvc
                .perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/v1/files/download/{id} is 401 without JWT")
    void download_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/files/download/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/v1/files/{id} delete is 401 without JWT")
    void delete_requiresAuth() throws Exception {
        mockMvc.perform(delete("/api/v1/files/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authorized GET /files returns 200 with valid JWT")
    void files_withJwt_listOk() throws Exception {
        // 1) Register a unique user
        String username = "itfile" + System.currentTimeMillis();
        String email = username + "@example.com";
        RegisterRequest register =
                RegisterRequest.builder().username(username).email(email).password("secret123").build();
        mockMvc
                .perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        // 2) Login to obtain token
        LoginRequest login = LoginRequest.builder().username(username).password("secret123").build();
        MvcResult loginResult =
                mockMvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn();

        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.token");

        // 3) Authorized GET /api/v1/files
        mockMvc
                .perform(get("/api/v1/files").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Authorized upload returns 200 with valid JWT")
    void upload_withJwt_ok() throws Exception {
        String username = "itupload" + System.currentTimeMillis();
        String email = username + "@example.com";
        RegisterRequest register =
                RegisterRequest.builder().username(username).email(email).password("secret123").build();
        mockMvc
                .perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        LoginRequest login = LoginRequest.builder().username(username).password("secret123").build();
        MvcResult loginResult =
                mockMvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn();
        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.token");

        MockMultipartFile file =
                new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());
        mockMvc
                .perform(
                        multipart("/api/v1/files/upload").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
