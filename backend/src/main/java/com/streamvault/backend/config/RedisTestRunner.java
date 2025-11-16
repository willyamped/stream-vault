package com.streamvault.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisTestRunner implements CommandLineRunner {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public void run(String... args) throws Exception {
        try (var connection = redisConnectionFactory.getConnection()) {
            log.info("✅ Connected to Redis: " + connection.ping());
        } catch (Exception e) {
            log.error("❌ Failed to connect to Redis: " + e.getMessage());
        }
    }
}
