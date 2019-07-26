/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.pistebot;

import com.digitalasset.ledger.LedgerAPI;
import com.prowidesoftware.swift.model.mt.mt2xx.MT202;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.function.Consumer;

public class Main {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws InterruptedException {
    String outputPath = System.getenv().getOrDefault("OUTPUT_PATH", "./output_messages");
    String sandboxHost = System.getenv().getOrDefault("SANDBOX_HOST", "localhost");
    int sandboxPort = Integer.parseInt(System.getenv().getOrDefault("SANDBOX_PORT", "7600"));

    LedgerAPI ledgerAPI = runBots(sandboxHost, sandboxPort, outputPath);

    System.out.println("Application started... Press Ctrl+C to stop it.");
    Thread.currentThread().join();
    ledgerAPI.stop();
  }

  public static LedgerAPI runBots(String sandboxHost, int sandboxPort, String outputPath) {
    File outputDir = createOutputDir(outputPath);
    Consumer<String> telegramSender;
    try {
      TelegramBot telegramBot = TelegramBot.start();
      telegramSender = telegramBot::sendMessage;
    } catch (Exception e) {
      logger.warn(
          "Error setting up the telegram bot. Telegram messages will be printed in the logs instead of sending them to telegram.",
          e);
      telegramSender = logger::info;
    }
    PisteBot bot = new PisteBot(telegramSender, swift -> writeToFile(outputDir, swift));
    return startLedgerAPI(sandboxHost, sandboxPort, bot);
  }

  private static LedgerAPI startLedgerAPI(String ledgerHost, int ledgerPort, PisteBot bot) {
    LedgerAPI ledgerAPI = new LedgerAPI(ledgerHost, ledgerPort);
    ledgerAPI.start();
    ledgerAPI.listenEvents("Intermediary", bot);
    return ledgerAPI;
  }

  private static void writeToFile(File outputDir, MT202 swiftMessage) {
    try {
      if (outputDir == null) return;
      // throws IllegalArgumentException if the parameter is not a proper UUID, Swift messages' UETR
      // should be a UUID
      UUID.fromString(swiftMessage.getUETR());
      PrintWriter pw =
          new PrintWriter(
              new File(outputDir, String.format("MT202_%s.txt", swiftMessage.getUETR())));
      pw.write(swiftMessage.message());
      pw.close();
    } catch (IllegalArgumentException iae) {
      logger.warn(
          "Swift message contains invalid UETR id. It is expected to be a proper UUID. Not writing the message into a file. Reason: {}",
          iae);
    } catch (FileNotFoundException e) {
      logger.warn("Could not write the message into a file. Reason: {}", e);
    }
  }

  private static File createOutputDir(String outputPath) {
    File dir = new File(outputPath);
    if (!dir.exists() && !dir.mkdirs())
      throw new IllegalStateException("Could not create output directory: " + outputPath);

    return dir;
  }
}
