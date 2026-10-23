package com.techno.berijalan.telegramservice.service.businessservice;

import com.techno.berijalan.telegramservice.domain.dto.request.ReqCreateTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqUpdateTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.request.ReqVerifyTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResTelegramBotDto;
import com.techno.berijalan.telegramservice.domain.dto.response.ResVerifyTelegramBotDto;

import java.util.List;

public interface TelegramBotBusinessService {

    ResVerifyTelegramBotDto verifyToken(ReqVerifyTelegramBotDto requestDto);

    ResTelegramBotDto registerBot(ReqCreateTelegramBotDto requestDto);

    ResTelegramBotDto updateBot(ReqUpdateTelegramBotDto requestDto);

    List<ResTelegramBotDto> getAllBots();

    ResTelegramBotDto getBotById(String id);

    ResTelegramBotDto deleteBot(String id);
}
