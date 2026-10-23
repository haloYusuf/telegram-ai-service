package com.techno.berijalan.telegramservice.service.dataservice;

import com.techno.berijalan.telegramservice.domain.entity.TelegramMessageEntity;

import java.util.List;
import java.util.Optional;

public interface TelegramMessageDataService {

    TelegramMessageEntity save(TelegramMessageEntity message);

    Optional<TelegramMessageEntity> findById(String id);

    List<TelegramMessageEntity> findByBucketIdOrderByTimestampAsc(String bucketId);

    Optional<TelegramMessageEntity> findLatestMessageByBucketId(String bucketId);

    List<TelegramMessageEntity> findByBotIdAndTelegramChatIdOrderByTimestampAsc(String botId, Long telegramChatId);
 
    Optional<TelegramMessageEntity> findByTelegramChatIdAndTelegramMessageId(Long telegramChatId, Long telegramMessageId);

    void deleteById(String id);
}

