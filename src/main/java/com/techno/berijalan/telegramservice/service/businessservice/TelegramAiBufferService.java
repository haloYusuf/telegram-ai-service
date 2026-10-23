package com.techno.berijalan.telegramservice.service.businessservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAiBufferService {

    public static final String BUFFER_MESSAGE_PREFIX = "telegram_buffer_message::";
    public static final String BUFFER_TIME_PREFIX = "telegram_buffer_time::";
    public static final String AI_CYCLE_PREFIX = "telegram_ai_cycle::";

    @Value("${agentic.buffer.ttl-seconds}")
    private long ttlSeconds;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Menambahkan pesan teks ke buffer Redis dan me-reset timer debounce 5 detik.
     */
    public boolean appendMessageAndResetTimer(String bucketId, String message) {
        String msgKey = BUFFER_MESSAGE_PREFIX + bucketId;
        String timeKey = BUFFER_TIME_PREFIX + bucketId;

        boolean isFirstMessage = !stringRedisTemplate.hasKey(timeKey);

        String existingBuffer = stringRedisTemplate.opsForValue().get(msgKey);
        String newBuffer = (existingBuffer == null || existingBuffer.isBlank())
                ? message
                : existingBuffer + "\n" + message;

        stringRedisTemplate.opsForValue().set(msgKey, newBuffer);
        stringRedisTemplate.opsForValue().set(timeKey, "1", Duration.ofSeconds(ttlSeconds));

        log.info("[TelegramAiBuffer] bucketId={}, isFirstMsg={}, bufferLength={}", bucketId, isFirstMessage, newBuffer.length());
        return isFirstMessage;
    }

    /**
     * Me-reset atau memperpanjang timer debounce 5 detik tanpa menambahkan pesan ke buffer.
     * Digunakan ketika nasabah mengirimkan file yang ditolak saat AI_HANDLING.
     */
    public void resetTimerOnly(String bucketId) {
        String timeKey = BUFFER_TIME_PREFIX + bucketId;
        stringRedisTemplate.opsForValue().set(timeKey, "1", Duration.ofSeconds(ttlSeconds));
        log.info("[TelegramAiBuffer] Extended/reset debounce timer only for bucketId={}", bucketId);
    }

    /**
     * Mengambil seluruh teks pesan yang terkumpul lalu menghapusnya dari buffer Redis.
     */
    public String flushBuffer(String bucketId) {
        String msgKey = BUFFER_MESSAGE_PREFIX + bucketId;
        String buffer = stringRedisTemplate.opsForValue().get(msgKey);
        stringRedisTemplate.delete(msgKey);
        log.info("[TelegramAiBuffer] Flushed buffer for bucketId={}, content='{}'", bucketId, buffer);
        return buffer;
    }

    /**
     * Membaca nomor siklus saat ini (1 sampai 5). Jika belum ada, default = 1.
     */
    public int getCycle(String bucketId) {
        String cycleKey = AI_CYCLE_PREFIX + bucketId;
        String val = stringRedisTemplate.opsForValue().get(cycleKey);
        if (val == null) {
            return 1;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Mengatur nomor siklus awal (1).
     */
    public void initCycle(String bucketId) {
        String cycleKey = AI_CYCLE_PREFIX + bucketId;
        stringRedisTemplate.opsForValue().set(cycleKey, "1", Duration.ofHours(24));
        log.info("[TelegramAiBuffer] Initialized AI cycle = 1 for bucketId={}", bucketId);
    }

    /**
     * Menaikkan siklus percakapan (+1).
     */
    public int incrementCycle(String bucketId) {
        String cycleKey = AI_CYCLE_PREFIX + bucketId;
        Long next = stringRedisTemplate.opsForValue().increment(cycleKey);
        int cycle = next != null ? next.intValue() : 1;
        log.info("[TelegramAiBuffer] Incremented AI cycle to {} for bucketId={}", cycle, bucketId);
        return cycle;
    }

    /**
     * Menghapus status siklus saat handover ke agen manusia.
     */
    public void clearCycle(String bucketId) {
        String cycleKey = AI_CYCLE_PREFIX + bucketId;
        stringRedisTemplate.delete(cycleKey);
        log.info("[TelegramAiBuffer] Cleared AI cycle for bucketId={}", bucketId);
    }
}
