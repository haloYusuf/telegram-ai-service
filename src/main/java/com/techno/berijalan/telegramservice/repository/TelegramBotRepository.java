package com.techno.berijalan.telegramservice.repository;

import com.techno.berijalan.telegramservice.domain.entity.TelegramBotEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramBotRepository extends MongoRepository<TelegramBotEntity, String> {

    Optional<TelegramBotEntity> findFirstByBotUsername(String botUsername);

    Optional<TelegramBotEntity> findFirstByBotToken(String botToken);

    Optional<TelegramBotEntity> findByIdAndIsActiveTrue(String id);

    List<TelegramBotEntity> findAllByIsActiveTrue();
}
