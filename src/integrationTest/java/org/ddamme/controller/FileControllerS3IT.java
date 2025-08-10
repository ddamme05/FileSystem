package org.ddamme.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.time.Duration;
import org.ddamme.dto.LoginRequest;
import org.ddamme.dto.RegisterRequest;
import org.ddamme.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Testcontainers
@Import(FileControllerS3IT.LocalStackS3Config.class)
class FileControllerS3IT extends BaseIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.6");

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.S3);

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
        RegisterRequest reg = RegisterRequest.builder()
                .username(user)
                .email(user + "@example.com")
                .password("secret123")
                .build();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        // Login
        LoginRequest login = LoginRequest.builder().username(user).password("secret123").build();
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();
        String token = JsonPath.read(loginRes.getResponse().getContentAsString(), "$.token");

        // Ensure bucket exists
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());

        // Upload
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());
        MvcResult uploadRes = mockMvc.perform(multipart("/files/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String uploadBody = uploadRes.getResponse().getContentAsString();
        System.out.println("Upload response: " + uploadBody);

        // Verify via list side-effect
        MvcResult listRes = mockMvc.perform(get("/files").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").exists())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].originalFilename").value("hello.txt"))
                .andReturn();

        Number idAsNumber = JsonPath.read(listRes.getResponse().getContentAsString(), "$.files[0].id");
        Long id = idAsNumber.longValue();

        // Presign
        mockMvc.perform(get("/files/download/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").isString());

        // Delete
        mockMvc.perform(delete("/files/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    static class LocalStackS3Config {
        @Bean
        @Primary
        S3Client s3ClientOverride() {
            return S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .region(Region.of(LOCALSTACK.getRegion()))
                    .endpointOverride(URI.create(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                    .build();
        }

        @Bean
        @Primary
        S3Presigner s3PresignerOverride() {
            return S3Presigner.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .region(Region.of(LOCALSTACK.getRegion()))
                    .endpointOverride(URI.create(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString()))
                    .build();
        }
    }
}


