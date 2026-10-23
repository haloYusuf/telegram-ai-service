package com.techno.berijalan.telegramservice.service.dataservice;

import com.techno.berijalan.telegramservice.domain.entity.TelegramSessionEntity;

import java.util.Optional;

public interface TelegramSessionDataService {

    TelegramSessionEntity save(TelegramSessionEntity session);

    Optional<TelegramSessionEntity> findById(String id);

    Optional<TelegramSessionEntity> findFirstByBotIdAndTelegramChatId(String botId, Long telegramChatId);

    void deleteById(String id);
}
