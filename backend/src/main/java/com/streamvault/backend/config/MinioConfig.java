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
    private String fileBucket;

    @Value("${minio.chunk-bucket}")
    private String chunkBucket;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            createBucketIfNotExists(client, fileBucket);
            createBucketIfNotExists(client, chunkBucket);

            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO", e);
        }
    }

    private void createBucketIfNotExists(MinioClient client, String bucketName) throws Exception {
        boolean exists = client.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );

        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            System.out.println("✅ Created MinIO bucket: " + bucketName);
        }
    }
}
