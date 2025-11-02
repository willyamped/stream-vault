package com.streamvault.backend.config;

import io.minio.MinioClient;
import io.minio.MakeBucketArgs;
import io.minio.BucketExistsArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            boolean found = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!found) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                System.out.println("✅ Created MinIO bucket: " + bucket);
            }
            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO", e);
        }
    }
}
