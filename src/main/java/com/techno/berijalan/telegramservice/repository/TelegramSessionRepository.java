package com.techno.berijalan.telegramservice.repository;

import com.techno.berijalan.telegramservice.domain.entity.TelegramSessionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramSessionRepository extends MongoRepository<TelegramSessionEntity, String> {

    Optional<TelegramSessionEntity> findFirstByBotIdAndTelegramChatId(String botId, Long telegramChatId);
}
