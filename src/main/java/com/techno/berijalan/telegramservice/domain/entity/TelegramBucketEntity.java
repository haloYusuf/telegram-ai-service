package com.techno.berijalan.telegramservice.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("telegram_bucket")
public class TelegramBucketEntity {

    @Id
    private String id;

    private String botId;              // Terhubung ke TelegramBotEntity mana

    private String tenantId;           // Tenant pemegang tiket

    private Long telegramChatId;       // ID chat unik nasabah dari Telegram

    private String customerUsername;   // Username Telegram nasabah (jika ada)

    private String customerFirstName;  // Nama depan nasabah

    private String customerPhoneNumber;// Nomor HP (jika dibagikan nasabah)

    // Status: "WAITING_AGENT", "ON_GOING", "RESOLVED"
    private String status;

    private String agentId;

    private String agentName;

    private Date createdAt;

    private Date updatedAt;
}
