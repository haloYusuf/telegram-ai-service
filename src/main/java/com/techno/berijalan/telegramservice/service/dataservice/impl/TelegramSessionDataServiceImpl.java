package com.techno.berijalan.telegramservice.service.dataservice.impl;

import com.techno.berijalan.telegramservice.domain.entity.TelegramSessionEntity;
import com.techno.berijalan.telegramservice.repository.TelegramSessionRepository;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramSessionDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramSessionDataServiceImpl implements TelegramSessionDataService {

    private final TelegramSessionRepository telegramSessionRepository;

    @Override
    public TelegramSessionEntity save(TelegramSessionEntity session) {
        return telegramSessionRepository.save(session);
    }

    @Override
    public Optional<TelegramSessionEntity> findById(String id) {
        return telegramSessionRepository.findById(id);
    }

    @Override
    public Optional<TelegramSessionEntity> findFirstByBotIdAndTelegramChatId(String botId, Long telegramChatId) {
        return telegramSessionRepository.findFirstByBotIdAndTelegramChatId(botId, telegramChatId);
    }

    @Override
    public void deleteById(String id) {
        telegramSessionRepository.deleteById(id);
    }
}
