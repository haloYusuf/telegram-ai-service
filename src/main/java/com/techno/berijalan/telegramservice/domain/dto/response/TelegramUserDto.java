package com.techno.berijalan.telegramservice.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUserDto {

    private Long id;

    @JsonProperty("is_bot")
    private Boolean isBot;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String username;

    @JsonProperty("can_join_groups")
    private Boolean canJoinGroups;

    @JsonProperty("can_read_all_group_messages")
    private Boolean canReadAllGroupMessages;

    @JsonProperty("supports_inline_queries")
    private Boolean supportsInlineQueries;
}
