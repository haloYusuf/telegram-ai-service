package com.techno.berijalan.telegramservice.service.businessservice.impl;

import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatEventDto;
import com.techno.berijalan.telegramservice.service.businessservice.TelegramSseEmitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class TelegramSseEmitterServiceImpl implements TelegramSseEmitterService {

    // Timeout 30 menit per koneksi streaming
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    // Map thread-safe dengan key botId untuk isolasi penuh antar bot
    private final Map<String, List<SseEmitter>> emittersByBot = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String botId) {
        if (!StringUtils.hasText(botId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter botId wajib diisi!");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        List<SseEmitter> list = emittersByBot.computeIfAbsent(botId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> {
            List<SseEmitter> currentList = emittersByBot.get(botId);
            if (currentList != null) {
                currentList.remove(emitter);
                if (currentList.isEmpty()) {
                    emittersByBot.remove(botId);
                }
            }
            log.info("SSE emitter disconnected & cleaned up for botId={}. Remaining active: {}",
                    botId, currentList != null ? currentList.size() : 0);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Kirim event inisialisasi agar koneksi HTTP 200 stream langsung aktif
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected to real-time chat stream for bot: " + botId));
            log.info("New SSE client subscribed for botId={}. Total active: {}", botId, list.size());
        } catch (IOException e) {
            log.warn("Failed to send initial SSE ping for botId={}: {}", botId, e.getMessage());
            cleanup.run();
        }

        return emitter;
    }

    @Override
    public void broadcastToBot(String botId, ResTelegramChatEventDto event) {
        if (!StringUtils.hasText(botId)) {
            return;
        }

        List<SseEmitter> list = emittersByBot.get(botId);
        if (list == null || list.isEmpty()) {
            log.debug("No active SSE subscribers for botId={}. Skip broadcast.", botId);
            return;
        }

        log.info("Broadcasting SSE event '{}' (bucketId={}) to {} clients for botId={}",
                event.getEventType(), event.getBucketId(), list.size(), botId);

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getEventType() != null ? event.getEventType() : "NEW_MESSAGE")
                        .data(event));
            } catch (Exception ex) {
                log.info("Client disconnected from SSE stream for botId={}: {}", botId, ex.getMessage());
                deadEmitters.add(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }

        if (!deadEmitters.isEmpty()) {
            list.removeAll(deadEmitters);
            if (list.isEmpty()) {
                emittersByBot.remove(botId);
            }
        }
    }

    /**
     * Heartbeat keep-alive setiap 25 detik:
     * 1. Menjaga koneksi tetap terbuka melewati proxy/firewall (seperti ngrok/nginx).
     * 2. Mendeteksi dan membuang socket mati (zombie connection) dari memori JVM secara instan.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        if (emittersByBot.isEmpty()) {
            return;
        }

        emittersByBot.forEach((botId, list) -> {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception ex) {
                    deadEmitters.add(emitter);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!deadEmitters.isEmpty()) {
                list.removeAll(deadEmitters);
            }
        });

        emittersByBot.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
