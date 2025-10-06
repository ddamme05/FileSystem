package org.ddamme.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.ddamme.config.AwsProperties;
import org.ddamme.exception.StorageOperationException;
import org.ddamme.metrics.Metrics;
import org.ddamme.util.FileUtils;
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

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;
    private final MeterRegistry meterRegistry;

    @Override
    @Observed(name = "s3.upload")
    public String upload(MultipartFile file) {
        String storageKey = generateStorageKey(file.getOriginalFilename());
        return upload(file, storageKey);
    }

    @Override
    @Observed(name = "s3.upload.with.key")
    public String upload(MultipartFile file, String storageKey) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            String bucketName = awsProperties.getS3().getBucketName();
            String contentType = FileUtils.getContentTypeOrDefault(file.getContentType());

            String originalName = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                    ? "file"
                    : file.getOriginalFilename();

            String ascii = originalName
                    .replaceAll("[^\\x20-\\x7E]", "_")
                    .replace("\"", "'")
                    .replace("\\", "_");

            String rfc5987 = FileUtils.rfc5987Encode(originalName);

            String contentDisposition = "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + rfc5987;

            PutObjectRequest putObjectRequest =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .contentType(contentType)
                            .contentDisposition(contentDisposition)
                            .contentLength(file.getSize())
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build();

            s3Client.putObject(
                    putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "put", "result", "success"));
            return storageKey;
        } catch (IOException | RuntimeException e) {
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "put", "result", "failure"));
            Metrics.increment(meterRegistry, "s3.op.errors", "op", "put", "error", e.getClass().getSimpleName());
            throw new StorageOperationException("Failed to upload file: " + file.getOriginalFilename(), e);
        }
    }

    private String generateStorageKey(String originalFilename) {
        String name = FileUtils.sanitizeFilename(originalFilename == null ? "file" : originalFilename);
        return UUID.randomUUID().toString() + "-" + name;
    }

    @Override
    @Observed(name = "s3.presign")
    public String generatePresignedDownloadUrl(String storageKey) {
        return generatePresignedDownloadUrl(storageKey, null);
    }

    @Override
    @Observed(name = "s3.presign.with.filename")
    public String generatePresignedDownloadUrl(String key, String originalName) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            String bucketName = awsProperties.getS3().getBucketName();

            // Build Content-Disposition header for presigned URL if original name provided
            GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key);
            
            if (originalName != null && !originalName.isBlank()) {
                // Create RFC 5987 compliant Content-Disposition for presigned URL
                String ascii = originalName
                        .replaceAll("[^\\x20-\\x7E]", "_")
                        .replace("\"", "'")
                        .replace("\\", "_");
                String rfc5987 = FileUtils.rfc5987Encode(originalName);
                String contentDisposition = "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + rfc5987;
                
                requestBuilder.responseContentDisposition(contentDisposition);
            }

            GetObjectRequest getObjectRequest = requestBuilder.build();

            GetObjectPresignRequest presign =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(awsProperties.getS3().getPresignTtlMinutes()))
                            .getObjectRequest(getObjectRequest)
                            .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presign);
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "get_presign", "result", "success"));
            return presigned.url().toString();
        } catch (RuntimeException e) {
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "get_presign", "result", "failure"));
            Metrics.increment(meterRegistry, "s3.op.errors", "op", "get_presign", "error", e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    @Observed(name = "s3.presign.view")
    public String generatePresignedViewUrl(String key, String originalName) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            String bucketName = awsProperties.getS3().getBucketName();

            // Build Content-Disposition header with "inline" for browser viewing
            GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key);
            
            if (originalName != null && !originalName.isBlank()) {
                // Create RFC 5987 compliant Content-Disposition for presigned URL
                String ascii = originalName
                        .replaceAll("[^\\x20-\\x7E]", "_")
                        .replace("\"", "'")
                        .replace("\\", "_");
                String rfc5987 = FileUtils.rfc5987Encode(originalName);
                // KEY DIFFERENCE: "inline" instead of "attachment"
                String contentDisposition = "inline; filename=\"" + ascii + "\"; filename*=UTF-8''" + rfc5987;
                
                requestBuilder.responseContentDisposition(contentDisposition);
            }

            GetObjectRequest getObjectRequest = requestBuilder.build();

            GetObjectPresignRequest presign =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(awsProperties.getS3().getPresignTtlMinutes()))
                            .getObjectRequest(getObjectRequest)
                            .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presign);
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "get_presign_view", "result", "success"));
            return presigned.url().toString();
        } catch (RuntimeException e) {
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "get_presign_view", "result", "failure"));
            Metrics.increment(meterRegistry, "s3.op.errors", "op", "get_presign_view", "error", e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    @Observed(name = "s3.delete")
    public void delete(String storageKey) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            String bucketName = awsProperties.getS3().getBucketName();

            DeleteObjectRequest deleteObjectRequest =
                    DeleteObjectRequest.builder().bucket(bucketName).key(storageKey).build();
            s3Client.deleteObject(deleteObjectRequest);
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "delete", "result", "success"));
        } catch (RuntimeException e) {
            sample.stop(Metrics.timer(meterRegistry, "s3.op.latency", "op", "delete", "result", "failure"));
            Metrics.increment(meterRegistry, "s3.op.errors", "op", "delete", "error", e.getClass().getSimpleName());
            throw e;
        }
    }
}
