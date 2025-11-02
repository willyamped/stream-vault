package com.streamvault.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisTestRunner implements CommandLineRunner {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public void run(String... args) throws Exception {
        try (var connection = redisConnectionFactory.getConnection()) {
            System.out.println("✅ Connected to Redis: " + connection.ping());
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to Redis: " + e.getMessage());
        }
    }
}
