package com.techno.berijalan.telegramservice.service.businessservice;

import com.techno.berijalan.telegramservice.domain.dto.TelegramUpdateDto;

public interface TelegramWebhookBusinessService {

    /**
     * Memproses webhook update yang masuk dari Telegram Cloud untuk bot tertentu.
     */
    void processIncomingUpdate(String botId, TelegramUpdateDto updateDto);
}
