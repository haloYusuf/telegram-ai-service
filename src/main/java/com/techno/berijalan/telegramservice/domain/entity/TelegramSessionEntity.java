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
@Document("telegram_session")
public class TelegramSessionEntity {

    @Id
    private String id;

    private String botId;

    private String tenantId;

    private Long telegramChatId;

    private Integer qnaCount;          // Menghitung jumlah percakapan (0 s.d. 5)

    private Boolean isTakeover;        // false = masih dijawab AI, true = sudah jadi tiket agen

    private String activeBucketId;     // ID bucket aktif saat ini (jika ada)

    private Date lastInteractionTime;
}
