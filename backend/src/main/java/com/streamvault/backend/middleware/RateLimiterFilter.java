package com.streamvault.backend.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final int SC_TOO_MANY_REQUESTS = 429;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        log.info("RateLimiter: Request from IP={}", clientIp);

        String key = "rate:" + clientIp;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW.toMillis();

        // Remove old timestamps outside the window
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count != null && count >= MAX_REQUESTS) {

            Set<Object> oldest = redisTemplate.opsForZSet().range(key, 0, 0);
            long reset = now;
            if (oldest != null && !oldest.isEmpty()) {
                reset = ((Long) oldest.iterator().next()).longValue() + WINDOW.toMillis();
            }

            response.setStatus(SC_TOO_MANY_REQUESTS);
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf((reset - now) / 1000));
            response.getWriter().write("Rate limit exceeded. Try again later.");
            return;
        }

        // Add current request timestamp
        redisTemplate.opsForZSet().add(key, now, now);
        redisTemplate.expire(key, WINDOW);

        long remaining = MAX_REQUESTS - (count != null ? count : 0);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(WINDOW.toSeconds()));

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
