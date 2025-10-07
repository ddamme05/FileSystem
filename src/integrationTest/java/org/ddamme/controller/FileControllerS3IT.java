package org.ddamme.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.ddamme.dto.LoginRequest;
import org.ddamme.dto.RegisterRequest;
import org.ddamme.testsupport.BaseIntegrationTest;
import org.ddamme.testsupport.LocalStackS3TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Testcontainers
@Import(LocalStackS3TestConfig.class)
@ActiveProfiles("it-localstack")
class FileControllerS3IT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Test
    @DisplayName("Full flow: Register -> Login -> Upload -> Presign -> Delete (S3 + DB)")
    void fullFlow_withLocalStack() throws Exception {
        // Register user
        String user = "its3user" + System.currentTimeMillis();
        RegisterRequest reg =
                RegisterRequest.builder()
                        .username(user)
                        .email(user + "@example.com")
                        .password("secret123")
                        .build();
        mockMvc
                .perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        // Login
        LoginRequest login = LoginRequest.builder().username(user).password("secret123").build();
        MvcResult loginRes =
                mockMvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.token").isString())
                        .andReturn();
        String token = JsonPath.read(loginRes.getResponse().getContentAsString(), "$.token");

        // Ensure bucket exists
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());

        // Upload
        MockMultipartFile file =
                new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());
        MvcResult uploadRes =
                mockMvc
                        .perform(
                                multipart("/api/v1/files/upload")
                                        .file(file)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();

        String uploadBody = uploadRes.getResponse().getContentAsString();

        // Verify via list side-effect
        MvcResult listRes =
                mockMvc
                        .perform(get("/api/v1/files").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.files").exists())
                        .andExpect(jsonPath("$.files.length()").value(1))
                        .andExpect(jsonPath("$.files[0].originalFilename").value("hello.txt"))
                        .andReturn();

        Number idAsNumber = JsonPath.read(listRes.getResponse().getContentAsString(), "$.files[0].id");
        Long id = idAsNumber.longValue();

        // Presign - expect 302 redirect to S3 URL
        MvcResult presignRes = mockMvc
                .perform(get("/api/v1/files/download/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isFound())  // 302
                .andExpect(header().exists("Location"))
                .andReturn();

        String redirectUrl = presignRes.getResponse().getHeader("Location");
        assertThat(redirectUrl).as("Should redirect to S3 presigned URL").isNotBlank();

        // Delete
        mockMvc
                .perform(delete("/api/v1/files/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName(
            "Presigned URL contains RFC 5987 Content-Disposition header for international filenames")
    void presignedUrl_containsRfc5987ContentDisposition() throws Exception {
        // Register user
        String user = "rfc5987user" + System.currentTimeMillis();
        RegisterRequest reg =
                RegisterRequest.builder()
                        .username(user)
                        .email(user + "@example.com")
                        .password("secret123")
                        .build();
        mockMvc
                .perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        // Login
        LoginRequest login = LoginRequest.builder().username(user).password("secret123").build();
        MvcResult loginRes =
                mockMvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.token").isString())
                        .andReturn();
        String token = JsonPath.read(loginRes.getResponse().getContentAsString(), "$.token");

        // Ensure bucket exists
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());

        // Upload file with international characters in filename
        String internationalFilename = "résumé_файл.pdf";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", internationalFilename, "application/pdf", "dummy pdf content".getBytes());

        mockMvc
                .perform(
                        multipart("/api/v1/files/upload").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Get the uploaded file ID
        MvcResult listRes =
                mockMvc
                        .perform(get("/api/v1/files").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.files[0].originalFilename").value(internationalFilename))
                        .andReturn();

        Number idAsNumber = JsonPath.read(listRes.getResponse().getContentAsString(), "$.files[0].id");
        Long id = idAsNumber.longValue();

        // Get presigned URL - expect 302 redirect
        MvcResult presignRes =
                mockMvc
                        .perform(get("/api/v1/files/download/" + id).header("Authorization", "Bearer " + token))
                        .andExpect(status().isFound())  // 302
                        .andExpect(header().exists("Location"))
                        .andReturn();

        String downloadUrl = presignRes.getResponse().getHeader("Location");

        // Verify the presigned URL contains RFC 5987 Content-Disposition parameters
        // The URL should contain both filename (ASCII fallback) and filename* (UTF-8 encoded)
        assertThat(downloadUrl)
                .as("Presigned URL should contain response-content-disposition parameter")
                .contains("response-content-disposition");

        assertThat(downloadUrl)
                .as("Should contain ASCII filename fallback")
                .contains("filename%3D%22r_sum______.pdf%22"); // URL-encoded: filename="r_sum______.pdf"
        // (sanitized)

        assertThat(downloadUrl)
                .as("Should contain RFC 5987 UTF-8 encoded filename")
                .contains(
                        "filename%2A%3DUTF-8%27%27r%25C3%25A9sum%25C3%25A9_%25D1%2584%25D0%25B0%25D0%25B9%25D0%25BB.pdf"); // URL-encoded: filename*=UTF-8''r%C3%A9sum%C3%A9_%D1%84%D0%B0%D0%B9%D0%BB.pdf
    }
}
