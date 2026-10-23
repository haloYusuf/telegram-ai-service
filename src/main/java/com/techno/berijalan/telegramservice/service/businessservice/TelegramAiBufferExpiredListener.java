package com.techno.berijalan.telegramservice.service.businessservice;

import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatEventDto;
import com.techno.berijalan.telegramservice.domain.dto.response.TelegramApiResponse;
import com.techno.berijalan.telegramservice.domain.entity.TelegramBotEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramBucketEntity;
import com.techno.berijalan.telegramservice.domain.entity.TelegramMessageEntity;
import com.techno.berijalan.telegramservice.rest.TelegramApiClient;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBotDataService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBucketDataService;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramMessageDataService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramAiBufferExpiredListener implements MessageListener {

    private final TelegramAiBufferService telegramAiBufferService;
    private final TelegramAiService telegramAiService;
    private final TelegramBucketDataService telegramBucketDataService;
    private final TelegramBotDataService telegramBotDataService;
    private final TelegramMessageDataService telegramMessageDataService;
    private final TelegramApiClient telegramApiClient;
    private final TelegramSseEmitterService telegramSseEmitterService;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    @PostConstruct
    public void registerListener() {
        redisMessageListenerContainer.addMessageListener(
                this,
                new PatternTopic("__keyevent@0__:expired")
        );
        log.info("Registered Redis keyspace expired listener for Telegram AI buffer");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody());

        if (!expiredKey.startsWith(TelegramAiBufferService.BUFFER_TIME_PREFIX)) {
            return;
        }

        String bucketId = expiredKey.substring(TelegramAiBufferService.BUFFER_TIME_PREFIX.length());
        log.info("[TelegramAiBuffer] Debounce timer 5s expired for bucketId={}, flushing buffer...", bucketId);

        String combinedMessages = telegramAiBufferService.flushBuffer(bucketId);
        if (combinedMessages == null || combinedMessages.isBlank()) {
            log.warn("[TelegramAiBuffer] Buffer is empty for bucketId={}, skip AI call", bucketId);
            return;
        }

        Optional<TelegramBucketEntity> bucketOpt = telegramBucketDataService.findById(bucketId);
        if (bucketOpt.isEmpty()) {
            log.error("[TelegramAiBuffer] Bucket with id={} not found", bucketId);
            return;
        }

        TelegramBucketEntity bucket = bucketOpt.get();

        // Ambil bot entity untuk token kirim balasan ke Telegram Cloud
        Optional<TelegramBotEntity> botOpt = telegramBotDataService.findById(bucket.getBotId());
        if (botOpt.isEmpty()) {
            log.error("[TelegramAiBuffer] Bot with id={} not found for bucketId={}", bucket.getBotId(), bucketId);
            return;
        }
        TelegramBotEntity bot = botOpt.get();

        int currentCycle = telegramAiBufferService.getCycle(bucketId);
        log.info("[TelegramAiBuffer] Processing cycle {} for bucketId={}", currentCycle, bucketId);

        // Generate respon dari Gemini AI
        String aiResponse;
        boolean handoverNeeded = false;
        try {
            aiResponse = telegramAiService.generateAiResponse(bucket, combinedMessages, currentCycle);
        } catch (Exception e) {
            log.warn("[TelegramAiBuffer] Gemini AI failed (e.g. Quota 429 / Service Error): {}. Initiating immediate handover to Human Agent.", e.getMessage());
            aiResponse = "Sebentar, sedang mengarahkan ke agent manusia";
            handoverNeeded = true;
        }

        // Kirim balasan ke Telegram Cloud via sendMessage
        Long sentAiTelegramMessageId = null;
        try {
            Map<String, Object> reqBody = Map.of(
                    "chat_id", bucket.getTelegramChatId(),
                    "text", aiResponse
            );
            TelegramApiResponse<Object> apiRes = telegramApiClient.sendMessage(bot.getBotToken(), reqBody);
            if (apiRes != null && apiRes.getResult() instanceof Map<?, ?> resMap) {
                Object mid = resMap.get("message_id");
                if (mid instanceof Number num) {
                    sentAiTelegramMessageId = num.longValue();
                }
            }
            log.info("[TelegramAiBuffer] AI response dispatched to Telegram customer: chatId={}, sentMsgId={}",
                    bucket.getTelegramChatId(), sentAiTelegramMessageId);
        } catch (Exception e) {
            log.error("[TelegramAiBuffer] Failed to send AI response to Telegram Cloud: {}", e.getMessage(), e);
        }

        // Simpan pesan AI ke database
        TelegramMessageEntity aiMsg = TelegramMessageEntity.builder()
                .botId(bucket.getBotId())
                .tenantId(bucket.getTenantId())
                .bucketId(bucket.getId())
                .telegramChatId(bucket.getTelegramChatId())
                .telegramMessageId(sentAiTelegramMessageId)
                .senderType("AI")
                .senderName("Gemini AI")
                .messageText(aiResponse)
                .mediaType("TEXT")
                .timestamp(new Date())
                .build();
        TelegramMessageEntity savedAiMsg = telegramMessageDataService.save(aiMsg);

        // Broadcast event SSE NEW_MESSAGE ke Frontend CS
        ResTelegramChatEventDto chatEvent = ResTelegramChatEventDto.builder()
                .eventType("NEW_MESSAGE")
                .botId(bucket.getBotId())
                .bucketId(bucket.getId())
                .telegramChatId(bucket.getTelegramChatId())
                .messageId(savedAiMsg != null ? savedAiMsg.getId() : null)
                .telegramMessageId(sentAiTelegramMessageId)
                .senderType("AI")
                .senderName("Gemini AI")
                .messageText(aiResponse)
                .mediaType("TEXT")
                .timestamp(new Date())
                .status(bucket.getStatus())
                .build();
        telegramSseEmitterService.broadcastToBot(bucket.getBotId(), chatEvent);

        // Cek apakah ini cycle ke-5 ATAU terjadi error AI (handoverNeeded) -> Handover ke Human Agent
        if (currentCycle >= 5 || handoverNeeded) {
            log.info("[TelegramAiBuffer] Handover triggered for bucketId={} (cycle={}, aiFailed={}). Handing over to Human Agent!",
                    bucketId, currentCycle, handoverNeeded);
            telegramAiBufferService.clearCycle(bucketId);

            bucket.setStatus("WAITING_AGENT");
            bucket.setUpdatedAt(new Date());
            telegramBucketDataService.save(bucket);

            // Broadcast SSE event AI_HANDOVER agar CS textfield langsung terbuka (unlocked)
            ResTelegramChatEventDto handoverEvent = ResTelegramChatEventDto.builder()
                    .eventType("AI_HANDOVER")
                    .botId(bucket.getBotId())
                    .bucketId(bucket.getId())
                    .telegramChatId(bucket.getTelegramChatId())
                    .senderType("SYSTEM")
                    .senderName("System")
                    .messageText(handoverNeeded
                            ? "Sebentar, sedang mengarahkan ke agent manusia"
                            : "Sesi 5 siklus AI selesai. Percakapan dialihkan ke Customer Service.")
                    .status("WAITING_AGENT")
                    .timestamp(new Date())
                    .build();
            telegramSseEmitterService.broadcastToBot(bucket.getBotId(), handoverEvent);
        } else {
            int nextCycle = telegramAiBufferService.incrementCycle(bucketId);
            log.info("[TelegramAiBuffer] Cycle incremented to {} for bucketId={}", nextCycle, bucketId);
        }
    }
}
