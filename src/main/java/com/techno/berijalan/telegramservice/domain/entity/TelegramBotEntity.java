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
@Document("telegram_bot")
public class TelegramBotEntity {

    @Id
    private String id;

    private String tenantId;

    private String botName;        // Contoh: "ACC Customer Care"

    private String botUsername;    // Contoh: "acc_official_bot"

    private String botToken;       // Token rahasia dari @BotFather

    private Boolean isActive;      // true / false

    private String photoUrl;       // URL stream MinIO untuk avatar bot

    private Date createdAt;

    private Date updatedAt;
}
