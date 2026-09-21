package com.sanyabhasin.queuepilot.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * A small Redis-backed lease semaphore. Redis is used here because the permit must be shared by
 * workers running in different processes; a JVM semaphore would only protect one worker.
 *
 * <p>The previous implementation checked the set size and added a token in separate commands,
 * which is racy under concurrent workers. The Lua script below makes the check-and-add atomic,
 * ensuring a destination cannot exceed its configured concurrency cap.
 */
@Component
public class RedisConcurrencyLimiter {
  private final StringRedisTemplate redis;

  public RedisConcurrencyLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public String tryAcquire(String destination, int limit, Duration ttl) {
    String token = UUID.randomUUID().toString();
    String key = "queuepilot:permit:" + destination;
    String script =
        "local count = redis.call('SCARD', KEYS[1]) "
            + "if count >= tonumber(ARGV[0]) then return nil end "
            + "redis.call('SADD', KEYS[1], ARGV[1]) "
            + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) "
            + "return ARGV[1]";

    DefaultRedisScript<String> redisScript = new DefaultRedisScript<>(script, String.class);
    return redis.execute(redisScript, java.util.Collections.singletonList(key), String.valueOf(limit), token, String.valueOf(ttl.getSeconds()));
  }

  public void release(String destination, String token) {
    if (token != null) {
      redis.opsForSet().remove("queuepilot:permit:" + destination, token);
    }
  }
}
