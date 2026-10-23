package com.techno.berijalan.telegramservice.service.businessservice;

import com.techno.berijalan.telegramservice.domain.dto.request.ReqSendTelegramMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResSendTelegramMessageDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramBucketDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramChatMessageDto;

import java.util.List;

public interface TelegramChatBusinessService {

    /**
     * Mengirim pesan balasan dari agen CS di web dashboard ke nasabah di Telegram.
     */
    ResSendTelegramMessageDto sendMessage(ReqSendTelegramMessageDto requestDto);

    /**
     * Mengambil riwayat pesan percakapan untuk room tiket tertentu secara kronologis.
     */
    List<ResTelegramChatMessageDto> getChatHistory(String bucketId);

    /**
     * Mengambil daftar antrean percakapan (CS Inbox / Buckets) untuk dashboard agen.
     */
    List<ResTelegramBucketDto> getBucketList(String botId, String status);

    /**
     * Mengubah status tiket secara dinamis (Compact 1 Endpoint: RESOLVED <-> ON_GOING).
     */
    ResTelegramBucketDto toggleBucketStatus(String bucketId);
}


