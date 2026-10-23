package com.techno.berijalan.telegramservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdateDto {

    @JsonProperty("update_id")
    private Long updateId;

    @JsonProperty("message")
    private MessageDto message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageDto {
        @JsonProperty("message_id")
        private Long messageId;

        @JsonProperty("from")
        private UserDto from;

        @JsonProperty("chat")
        private ChatDto chat;

        @JsonProperty("date")
        private Long date;

        @JsonProperty("text")
        private String text;

        @JsonProperty("caption")
        private String caption;

        @JsonProperty("photo")
        private List<PhotoSizeDto> photo;

        @JsonProperty("document")
        private DocumentDto document;

        @JsonProperty("video")
        private VideoDto video;

        @JsonProperty("contact")
        private ContactDto contact;

        @JsonProperty("media_group_id")
        private String mediaGroupId;

        @JsonProperty("reply_to_message")
        private MessageDto replyToMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContactDto {
        @JsonProperty("phone_number")
        private String phoneNumber;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("user_id")
        private Long userId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserDto {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("is_bot")
        private Boolean isBot;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("username")
        private String username;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatDto {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("username")
        private String username;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PhotoSizeDto {
        @JsonProperty("file_id")
        private String fileId;

        @JsonProperty("file_size")
        private Long fileSize;

        @JsonProperty("width")
        private Integer width;

        @JsonProperty("height")
        private Integer height;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentDto {
        @JsonProperty("file_id")
        private String fileId;

        @JsonProperty("file_name")
        private String fileName;

        @JsonProperty("mime_type")
        private String mimeType;

        @JsonProperty("file_size")
        private Long fileSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoDto {
        @JsonProperty("file_id")
        private String fileId;

        @JsonProperty("file_name")
        private String fileName;

        @JsonProperty("mime_type")
        private String mimeType;

        @JsonProperty("file_size")
        private Long fileSize;

        @JsonProperty("duration")
        private Integer duration;
    }
}
