package org.ddamme.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.MetadataRepository;
import org.ddamme.database.repository.UserRepository;
import org.ddamme.dto.LoginRequest;
import org.ddamme.dto.RegisterRequest;
import org.ddamme.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FileAuthorizationIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private MetadataRepository metadataRepository;

  @Test
  @DisplayName("Accessing another user's file returns 404")
  void crossUser_access_returns_not_found() throws Exception {
    // Register owner (A)
    String userA = "owner" + System.currentTimeMillis();
    RegisterRequest regA =
        RegisterRequest.builder()
            .username(userA)
            .email(userA + "@example.com")
            .password("secret123")
            .build();
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regA)))
        .andExpect(status().isOk());

    // Load owner entity
    User owner = userRepository.findByUsername(userA).orElseThrow();

    // Create a file metadata owned by A
    FileMetadata fm =
        FileMetadata.builder()
            .originalFilename("doc.txt")
            .storageKey("s3/key/" + System.nanoTime())
            .size(10L)
            .contentType("text/plain")
            .uploadTimestamp(Instant.now())
            .user(owner)
            .build();
    fm = metadataRepository.save(fm);

    // Register attacker (B)
    String userB = "attacker" + System.currentTimeMillis();
    RegisterRequest regB =
        RegisterRequest.builder()
            .username(userB)
            .email(userB + "@example.com")
            .password("secret123")
            .build();
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regB)))
        .andExpect(status().isOk());

    // Login as B
    LoginRequest loginB = LoginRequest.builder().username(userB).password("secret123").build();
    MvcResult loginRes =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginB)))
            .andExpect(status().isOk())
            .andReturn();
    String tokenB = JsonPath.read(loginRes.getResponse().getContentAsString(), "$.token");

    // B attempts to download A's file -> 404 (prevents existence probing)
    mockMvc
        .perform(
            get("/api/v1/files/download/" + fm.getId()).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());

    // B attempts to delete A's file -> 404 (prevents existence probing)
    mockMvc
        .perform(delete("/api/v1/files/" + fm.getId()).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
  }
}
