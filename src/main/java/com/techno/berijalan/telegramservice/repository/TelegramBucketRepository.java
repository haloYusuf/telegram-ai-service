package com.techno.berijalan.telegramservice.repository;

import com.techno.berijalan.telegramservice.domain.entity.TelegramBucketEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramBucketRepository extends MongoRepository<TelegramBucketEntity, String> {

    // Cari tiket aktif yang belum di-resolve untuk nasabah tertentu di bot ini
    Optional<TelegramBucketEntity> findFirstByBotIdAndTelegramChatIdAndStatusNot(String botId, Long telegramChatId, String status);

    // Cari tiket paling terakhir untuk nasabah tertentu di bot ini (apapun statusnya)
    Optional<TelegramBucketEntity> findFirstByBotIdAndTelegramChatIdOrderByUpdatedAtDesc(String botId, Long telegramChatId);

    // Ambil daftar bucket per Bot berdasarkan status (misal: WAITING_AGENT)
    List<TelegramBucketEntity> findByBotIdAndStatusOrderByUpdatedAtDesc(String botId, String status);

    // Ambil semua bucket per Bot
    List<TelegramBucketEntity> findByBotIdOrderByUpdatedAtDesc(String botId);

    // Ambil daftar semua bucket terurut aktivitas terbaru
    List<TelegramBucketEntity> findAllByOrderByUpdatedAtDesc();

    // Ambil daftar semua bucket berdasarkan status terurut aktivitas terbaru
    List<TelegramBucketEntity> findByStatusOrderByUpdatedAtDesc(String status);
}

