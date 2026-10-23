package com.techno.berijalan.telegramservice.repository;

import com.techno.berijalan.telegramservice.domain.entity.TelegramMessageEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramMessageRepository extends MongoRepository<TelegramMessageEntity, String> {

    // Ambil riwayat chat berdasarkan bucket_id urut dari yang paling lama ke baru
    List<TelegramMessageEntity> findByBucketIdOrderByTimestampAsc(String bucketId);

    // Ambil pesan terakhir pada bucket (untuk cuplikan preview pesan di UI)
    Optional<TelegramMessageEntity> findFirstByBucketIdOrderByTimestampDesc(String bucketId);

    // Ambil riwayat chat nasabah sebelum ada bucket (berdasarkan chat_id)
    List<TelegramMessageEntity> findByBotIdAndTelegramChatIdOrderByTimestampAsc(String botId, Long telegramChatId);

    // Cari pesan asli Telegram berdasarkan chat ID dan telegram message ID untuk relasi reply
    Optional<TelegramMessageEntity> findByTelegramChatIdAndTelegramMessageId(Long telegramChatId, Long telegramMessageId);

    // Cari semua pesan dalam 1 media group
    List<TelegramMessageEntity> findByMediaGroupId(String mediaGroupId);
}

