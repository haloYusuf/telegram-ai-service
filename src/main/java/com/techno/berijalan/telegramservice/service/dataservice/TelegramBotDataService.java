package com.techno.berijalan.telegramservice.service.dataservice;

import com.techno.berijalan.telegramservice.domain.entity.TelegramBotEntity;

import java.util.List;
import java.util.Optional;

public interface TelegramBotDataService {

    TelegramBotEntity save(TelegramBotEntity bot);

    Optional<TelegramBotEntity> findById(String id);

    Optional<TelegramBotEntity> findByIdAndIsActiveTrue(String id);

    Optional<TelegramBotEntity> findFirstByBotToken(String botToken);

    Optional<TelegramBotEntity> findFirstByBotUsername(String botUsername);

    List<TelegramBotEntity> findAllByIsActiveTrue();

    List<TelegramBotEntity> findAll();

    void deleteById(String id);
}
