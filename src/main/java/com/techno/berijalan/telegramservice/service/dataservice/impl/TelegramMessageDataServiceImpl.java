package com.techno.berijalan.telegramservice.service.dataservice.impl;

import com.techno.berijalan.telegramservice.domain.entity.TelegramMessageEntity;
import com.techno.berijalan.telegramservice.repository.TelegramMessageRepository;
import com.techno.berijalan.telegramservice.service.dataservice.TelegramMessageDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramMessageDataServiceImpl implements TelegramMessageDataService {

    private final TelegramMessageRepository telegramMessageRepository;

    @Override
    public TelegramMessageEntity save(TelegramMessageEntity message) {
        return telegramMessageRepository.save(message);
    }

    @Override
    public Optional<TelegramMessageEntity> findById(String id) {
        return telegramMessageRepository.findById(id);
    }

    @Override
    public List<TelegramMessageEntity> findByBucketIdOrderByTimestampAsc(String bucketId) {
        return telegramMessageRepository.findByBucketIdOrderByTimestampAsc(bucketId);
    }

    @Override
    public Optional<TelegramMessageEntity> findLatestMessageByBucketId(String bucketId) {
        return telegramMessageRepository.findFirstByBucketIdOrderByTimestampDesc(bucketId);
    }

    @Override
    public List<TelegramMessageEntity> findByBotIdAndTelegramChatIdOrderByTimestampAsc(String botId, Long telegramChatId) {
        return telegramMessageRepository.findByBotIdAndTelegramChatIdOrderByTimestampAsc(botId, telegramChatId);
    }

    @Override
    public Optional<TelegramMessageEntity> findByTelegramChatIdAndTelegramMessageId(Long telegramChatId, Long telegramMessageId) {
        return telegramMessageRepository.findByTelegramChatIdAndTelegramMessageId(telegramChatId, telegramMessageId);
    }

    @Override
    public void deleteById(String id) {
        telegramMessageRepository.deleteById(id);
    }
}
