package com.streamvault.backend.config;

import io.minio.MinioClient;
import io.minio.MakeBucketArgs;
import io.minio.BucketExistsArgs;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Collections;

@Slf4j
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

    private static final Duration OBJECT_TTL = Duration.ofHours(24);

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            createBucketIfNotExists(client, fileBucket);
            createBucketIfNotExists(client, chunkBucket);
            // setBucketTTL(client, chunkBucket, OBJECT_TTL);
            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO", e);
        }
    }

    private void createBucketIfNotExists(MinioClient client, String bucketName) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Created MinIO bucket: {}", bucketName);
        }
    }

    private void setBucketTTL(MinioClient client, String bucketName, Duration ttl) throws Exception {
        int expireDays = (int) Math.max(1, ttl.toDays());
        Expiration expiration = new Expiration((ResponseDate) null, expireDays, null);

        LifecycleRule rule = new LifecycleRule(
                Status.ENABLED,
                null,
                expiration,
                null,
                "Auto-delete expired chunks",
                null, null, null
        );

        LifecycleConfiguration config = new LifecycleConfiguration(Collections.singletonList(rule));

        client.setBucketLifecycle(
                SetBucketLifecycleArgs.builder()
                        .bucket(bucketName)
                        .config(config)
                        .build()
        );

        log.info("Set TTL for bucket {} to {} hours", bucketName, ttl.toHours());
    }
}