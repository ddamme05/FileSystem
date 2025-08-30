package org.ddamme.service;

import lombok.RequiredArgsConstructor;
import org.ddamme.config.AwsProperties;
import org.ddamme.exception.StorageOperationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import io.micrometer.observation.annotation.Observed;

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;
    

    @Override
    @Observed(name = "s3.upload")
    public String upload(MultipartFile file) {
        String storageKey = generateStorageKey(file.getOriginalFilename());
        return upload(file, storageKey);
    }

    @Override
    @Observed(name = "s3.upload.with.key")
    public String upload(MultipartFile file, String storageKey) {
        String bucketName = awsProperties.getS3().getBucketName();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .serverSideEncryption(ServerSideEncryption.AES256) // ADD THIS
                .build();
        try {
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return storageKey;
        } catch (IOException e) {
            throw new StorageOperationException("Failed to upload file: " + file.getOriginalFilename(), e);
        }
    }

    private String generateStorageKey(String originalFilename) {
        return UUID.randomUUID().toString() + "-" + originalFilename;
    }

    @Override
    @Observed(name = "s3.presign")
    public String generatePresignedDownloadUrl(String storageKey) {
        return generatePresignedDownloadUrl(storageKey, null);
    }

    @Override
    @Observed(name = "s3.presign.with.filename")
    public String generatePresignedDownloadUrl(String key, String originalName) {
        String bucketName = awsProperties.getS3().getBucketName();
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key);
        
        if (originalName != null) {
            requestBuilder.responseContentDisposition("attachment; filename=\"" + originalName + "\"");
        }
        
        GetObjectRequest getObjectRequest = requestBuilder.build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        return presignedRequest.url().toString();
    }

    @Override
    @Observed(name = "s3.delete")
    public void delete(String storageKey) {
        String bucketName = awsProperties.getS3().getBucketName();

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }
} 