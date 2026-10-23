package com.techno.berijalan.telegramservice.service.dataservice.impl;

import com.techno.berijalan.telegramservice.domain.entity.TelegramBotEntity;
import com.techno.berijalan.telegramservice.repository.TelegramBotRepository;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBotDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotDataServiceImpl implements TelegramBotDataService {

    private final TelegramBotRepository telegramBotRepository;

    @Override
    public TelegramBotEntity save(TelegramBotEntity bot) {
        return telegramBotRepository.save(bot);
    }

    @Override
    public Optional<TelegramBotEntity> findById(String id) {
        return telegramBotRepository.findById(id);
    }

    @Override
    public Optional<TelegramBotEntity> findByIdAndIsActiveTrue(String id) {
        return telegramBotRepository.findByIdAndIsActiveTrue(id);
    }

    @Override
    public Optional<TelegramBotEntity> findFirstByBotToken(String botToken) {
        return telegramBotRepository.findFirstByBotToken(botToken);
    }

    @Override
    public Optional<TelegramBotEntity> findFirstByBotUsername(String botUsername) {
        return telegramBotRepository.findFirstByBotUsername(botUsername);
    }

    @Override
    public List<TelegramBotEntity> findAllByIsActiveTrue() {
        return telegramBotRepository.findAllByIsActiveTrue();
    }

    @Override
    public List<TelegramBotEntity> findAll() {
        return telegramBotRepository.findAll();
    }

    @Override
    public void deleteById(String id) {
        telegramBotRepository.deleteById(id);
    }
}
