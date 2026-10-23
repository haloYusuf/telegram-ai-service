package com.techno.berijalan.telegramservice.service.businessservice;

import com.techno.berijalan.telegramservice.domain.constants.PathConstant;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @PostConstruct
    public void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinIO] Created bucket '{}' successfully", bucketName);
            } else {
                log.info("[MinIO] Bucket '{}' already exists", bucketName);
            }
        } catch (Exception e) {
            log.warn("[MinIO] Could not initialize bucket '{}': {}", bucketName, e.getMessage());
        }
    }

    public String uploadFile(String objectName, byte[] data, String contentType) {
        try {
            if (contentType == null || contentType.isBlank()) {
                contentType = determineContentType(objectName);
            }

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());

            log.info("[MinIO] Successfully uploaded object '{}' ({} bytes, type={})", objectName, data.length, contentType);
            return objectName;
        } catch (Exception e) {
            log.error("[MinIO] Failed to upload object '{}': {}", objectName, e.getMessage(), e);
            throw new RuntimeException("Gagal mengunggah media ke MinIO: " + e.getMessage(), e);
        }
    }

    public byte[] downloadFile(String objectName) {
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.error("[MinIO] Failed to download object '{}': {}", objectName, e.getMessage(), e);
            throw new RuntimeException("Gagal mengunduh media dari MinIO: " + e.getMessage(), e);
        }
    }

    public String buildStreamUrl(String objectName) {
        if (objectName == null) return null;
        return PathConstant.ROOT_ENDPOINT_CONTEXT
                + PathConstant.URI_TELEGRAM_MEDIA_STREAM
                + "?path=" + URLEncoder.encode(objectName, StandardCharsets.UTF_8);
    }

    public String determineContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

}
