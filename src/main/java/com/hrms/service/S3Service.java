package com.hrms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class S3Service {

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Value("${aws.access-key:}")
    private String accessKey;

    @Value("${aws.secret-key:}")
    private String secretKey;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${app.upload-dir:uploads}")
    private String localUploadDir;

    private S3Client s3Client;
    private boolean isS3Configured = false;

    @PostConstruct
    public void init() {
        if (bucketName != null && !bucketName.isBlank() && 
            accessKey != null && !accessKey.isBlank() && 
            secretKey != null && !secretKey.isBlank()) {
            try {
                s3Client = S3Client.builder()
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                        .region(Region.of(region))
                        .build();
                isS3Configured = true;
                System.out.println("[S3Service] AWS S3 Client initialized successfully. Bucket: " + bucketName);
            } catch (Exception e) {
                System.err.println("[S3Service] Failed to initialize S3 client: " + e.getMessage() + ". Falling back to local storage.");
            }
        } else {
            System.out.println("[S3Service] AWS S3 is not configured. Local storage fallback will be used.");
        }
    }

    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_") : "file";
        String uniqueFilename = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizedFilename;
        String key = folder + "/" + uniqueFilename;

        if (isS3Configured) {
            try {
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(key)
                                .contentType(file.getContentType())
                                .build(),
                        RequestBody.fromInputStream(file.getInputStream(), file.getSize())
                );
                // Return S3 URL
                return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
            } catch (Exception e) {
                System.err.println("[S3Service] S3 upload failed: " + e.getMessage() + ". Falling back to local storage.");
            }
        }

        // Local storage fallback
        try {
            Path targetDir = Paths.get(localUploadDir, "documents", folder);
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/documents/" + folder + "/" + uniqueFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file locally: " + e.getMessage(), e);
        }
    }
}
