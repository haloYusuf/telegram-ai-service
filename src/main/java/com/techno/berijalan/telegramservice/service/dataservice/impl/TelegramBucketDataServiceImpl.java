package com.techno.berijalan.telegramservice.service.dataservice.impl;

import com.techno.berijalan.telegramservice.domain.entity.TelegramBucketEntity;
import com.techno.berijalan.telegramservice.repository.TelegramBucketRepository;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramBucketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBucketDataServiceImpl implements TelegramBucketDataService {

    private final TelegramBucketRepository telegramBucketRepository;

    @Override
    public TelegramBucketEntity save(TelegramBucketEntity bucket) {
        return telegramBucketRepository.save(bucket);
    }

    @Override
    public Optional<TelegramBucketEntity> findById(String id) {
        return telegramBucketRepository.findById(id);
    }

    @Override
    public Optional<TelegramBucketEntity> findFirstByBotIdAndTelegramChatIdAndStatusNot(String botId, Long telegramChatId, String status) {
        return telegramBucketRepository.findFirstByBotIdAndTelegramChatIdAndStatusNot(botId, telegramChatId, status);
    }

    @Override
    public Optional<TelegramBucketEntity> findFirstByBotIdAndTelegramChatIdOrderByUpdatedAtDesc(String botId, Long telegramChatId) {
        return telegramBucketRepository.findFirstByBotIdAndTelegramChatIdOrderByUpdatedAtDesc(botId, telegramChatId);
    }

    @Override
    public List<TelegramBucketEntity> findByBotIdAndStatusOrderByUpdatedAtDesc(String botId, String status) {
        return telegramBucketRepository.findByBotIdAndStatusOrderByUpdatedAtDesc(botId, status);
    }

    @Override
    public List<TelegramBucketEntity> findByBotIdOrderByUpdatedAtDesc(String botId) {
        return telegramBucketRepository.findByBotIdOrderByUpdatedAtDesc(botId);
    }

    @Override
    public List<TelegramBucketEntity> findAllByOrderByUpdatedAtDesc() {
        return telegramBucketRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Override
    public List<TelegramBucketEntity> findByStatusOrderByUpdatedAtDesc(String status) {
        return telegramBucketRepository.findByStatusOrderByUpdatedAtDesc(status);
    }

    @Override
    public void deleteById(String id) {
        telegramBucketRepository.deleteById(id);
    }
}

