package com.techno.berijalan.telegramservice.service.dataservice;

import com.techno.berijalan.telegramservice.domain.entity.TelegramBucketEntity;

import java.util.List;
import java.util.Optional;

public interface TelegramBucketDataService {

    TelegramBucketEntity save(TelegramBucketEntity bucket);

    Optional<TelegramBucketEntity> findById(String id);

    Optional<TelegramBucketEntity> findFirstByBotIdAndTelegramChatIdAndStatusNot(String botId, Long telegramChatId, String status);

    Optional<TelegramBucketEntity> findFirstByBotIdAndTelegramChatIdOrderByUpdatedAtDesc(String botId, Long telegramChatId);

    List<TelegramBucketEntity> findByBotIdAndStatusOrderByUpdatedAtDesc(String botId, String status);

    List<TelegramBucketEntity> findByBotIdOrderByUpdatedAtDesc(String botId);

    List<TelegramBucketEntity> findAllByOrderByUpdatedAtDesc();

    List<TelegramBucketEntity> findByStatusOrderByUpdatedAtDesc(String status);

    void deleteById(String id);
}

