package com.techno.berijalan.telegramservice.service.businessservice;

import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatEventDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface TelegramSseEmitterService {

    /**
     * Membuka koneksi SSE streaming untuk klien web CS yang sedang membuka bot tertentu.
     *
     * @param botId ID bot Telegram yang dimonitor
     * @return SseEmitter instance
     */
    SseEmitter subscribe(String botId);

    /**
     * Memancarkan event real-time (pesan baru / update tiket) ke seluruh CS yang membuka bot tersebut.
     *
     * @param botId ID bot Telegram penerima
     * @param event payload event real-time ringkas
     */
    void broadcastToBot(String botId, ResTelegramChatEventDto event);
}
