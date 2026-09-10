package com.wallet.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Defends against duplicate money operations using Redis.
 *
 * Flow:
 * 1. Client sends an `Idempotency-Key` header alongside a deposit/withdraw/transfer.
 * 2. We try `SET key processing EX 120 NX` (acquire a short-lived lock).
 *    - We acquired it -> FIRST time -> proceed with the operation.
 *    - We didn't     -> duplicate/in-flight -> the caller must NOT process again.
 * 3. After the DB commit, we DEL the "processing" key.
 *
 * Note: the DB unique constraint on (wallet_id, idempotency_key) is the REAL final
 * guard. Redis lock is a fast first line of defence. Even if Redis were cleared
 * mid-flight, the database would still reject a second identical operation.
 *
 * SET NX is ATOMIC: two parallel identical requests can never both acquire it.
 */
@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String lockKey(String key) {
        return "idempotency:" + key;
    }

    /**
     * Try to acquire the idempotency lock.
     * @return true if WE got the lock (this is the first request); false if in-flight/duplicate.
     */
    public boolean tryAcquire(String key) {
        Boolean didNotExist = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(key), "processing", Duration.ofMinutes(2));
        return Boolean.TRUE.equals(didNotExist);
    }

    /** Release the lock. Only call this if YOU acquired it. */
    public void release(String key) {
        redisTemplate.delete(lockKey(key));
    }
}