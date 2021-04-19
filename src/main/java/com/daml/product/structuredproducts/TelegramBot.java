/*
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.structuredproducts;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

public class TelegramBot extends TelegramLongPollingBot {
  private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);

  static TelegramBot start() throws TelegramApiRequestException, IOException {
    try (InputStream is = new FileInputStream("telegram.properties")) {
      Properties telegramProperties = new Properties();
      telegramProperties.load(is);

      String botToken =
          requireNonNull(
              emptyToNull(telegramProperties.getProperty("BOT_TOKEN")),
              "Please provide a telegram token in the BOT_TOKEN environment variable");
      String chatId =
          requireNonNull(
              emptyToNull(telegramProperties.getProperty("CHAT_ID")),
              "Please provide a telegram chat id in the CHAT_ID environment variable");

      ApiContextInitializer.init();
      TelegramBot telegramBot = new TelegramBot(botToken, chatId);
      TelegramBotsApi botsApi = new TelegramBotsApi();
      botsApi.registerBot(telegramBot);
      telegramBot.sendMessage("Bot started");
      return telegramBot;
    }
  }

  private final String botToken;
  private final String chatId;

  private TelegramBot(String botToken, String chatId) {
    this.botToken = botToken;
    this.chatId = chatId;
  }

  /** Upon receiving a new message it relays it to the user's Telegram application */
  @Override
  public void onUpdateReceived(Update update) {
    logger.info("Telegram update received: {}", update);
    if (update.hasMessage() && update.getMessage().hasText()) {
      SendMessage message =
          new SendMessage()
              .setChatId(update.getMessage().getChatId())
              .setText(update.getMessage().getText());
      try {
        execute(message);
      } catch (TelegramApiException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public String getBotUsername() {
    return "";
  }

  @Override
  public String getBotToken() {
    return botToken;
  }

  /** Sending a message directly to the users's Telegram application */
  void sendMessage(String text) {
    logger.info("Sending Telegram message: {}", text);
    SendMessage message = new SendMessage().setChatId(chatId).setText(text);
    try {
      execute(message);
    } catch (TelegramApiException e) {
      logger.error("Error sending Telegram message", e);
    }
  }
}
