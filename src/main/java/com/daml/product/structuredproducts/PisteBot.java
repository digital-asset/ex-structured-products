/*
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.structuredproducts;

import com.daml.ledger.javaapi.data.CreatedEvent;
import com.daml.ledger.javaapi.data.DamlRecord;
import com.daml.ledger.javaapi.data.Event;
import com.prowidesoftware.swift.model.field.Field20;
import com.prowidesoftware.swift.model.field.Field21;
import com.prowidesoftware.swift.model.field.Field32A;
import com.prowidesoftware.swift.model.field.Field58A;
import com.prowidesoftware.swift.model.mt.mt2xx.MT202;
import da.refapps.structuredproducts.dcn.CouponEvent;
import da.refapps.structuredproducts.dcn.KnockOutEvent;
import da.refapps.structuredproducts.dcn.PaymentInstructions;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This bot listens to events and acts on CouponEvents, KnockOutEvent and PaymentInstructions.
 *
 * <p>It sends Telegram messages (if Telegram integration is set up) and writes swift messages into
 * files.
 */
public class PisteBot implements Consumer<Event> {
  private static final Logger logger = LoggerFactory.getLogger(PisteBot.class);

  private static final String UNKNOWN = "unknown";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyMMdd");
  private final Consumer<String> messaging;
  private final Consumer<MT202> swiftWriter;

  PisteBot(Consumer<String> messaging, Consumer<MT202> swiftWriter) {
    this.messaging = messaging;
    this.swiftWriter = swiftWriter;
  }

  /** Sending the text to Telegram if Telegram integration is set up. */
  void sendNotification(String text) {
    if (messaging != null) {
      messaging.accept(text);
    }
  }

  /**
   * Upon receiving an event it checks its type to determine if it is for this bot. Then invokes
   * processors specific to the type of the event.
   *
   * @param event
   */
  @Override
  public void accept(Event event) {
    logger.debug("Accepted event: {}", event);
    try {
      if (event instanceof CreatedEvent) {
        CreatedEvent ce = (CreatedEvent) event;
        DamlRecord args = ce.getArguments();
        if (CouponEvent.TEMPLATE_ID.equals(event.getTemplateId())) {
          processCouponEvent(CouponEvent.fromValue(args));
        } else if (KnockOutEvent.TEMPLATE_ID.equals(event.getTemplateId())) {
          processKnockOutEvent(KnockOutEvent.fromValue(args));
        } else if (PaymentInstructions.TEMPLATE_ID.equals(event.getTemplateId())) {
          processPaymentMessage(PaymentInstructions.fromValue(args));
        }
      }
    } catch (RuntimeException e) {
      logger.error(String.format("Error processing event %s", event), e);
      throw e;
    }
  }

  /** Processing a coupon event. */
  void processCouponEvent(CouponEvent event) {
    logger.debug("CouponEvent received: {}", event);
    sendNotification(
        String.format(
            "Coupon event occurred on trade %s between %s and %s",
            event.tradeId, event.issuer, event.owner));
  }

  /** Processing a knock out event. */
  void processKnockOutEvent(KnockOutEvent event) {
    logger.debug("KnockOutEvent received: {}", event);
    String tradeId = event.tradeId;
    String koReason =
        Optional.ofNullable(event.knockOutReason)
            .orElse(PisteBot.UNKNOWN); // fields.getOrDefault("knockOutReason",
    // Unit.getInstance()).asText().orElse(PisteBot.UNKNOWN).getValue();
    sendNotification(String.format("DCN %s has knocked out, reason: %s", tradeId, koReason));
  }

  /** Processing payment instruction. */
  void processPaymentMessage(PaymentInstructions event) {
    logger.debug("PaymentInstruction received: {}", event);
    MT202 swiftMessage = convertToSwift(event);
    logger.info("Sending SWIFT message: {}", swiftMessage.message());
    swiftWriter.accept(swiftMessage);

    sendNotification(
        String.format(
            "SWIFT transfer initiated from %s to beneficiary %s for %s %s on %s (ref=%s, id=%s)",
            swiftMessage.getSender(),
            swiftMessage.getReceiver(),
            swiftMessage.getField32A().amount().toString(),
            swiftMessage.getField32A().currency(),
            swiftMessage.getField32A().getDate(),
            swiftMessage.getField20().getReference(),
            swiftMessage.getUETR()));
  }

  /**
   * Creates valid Swift message from the received payment instructions.
   *
   * @param pi payment instruction
   * @return an MT202 Swift message based on the info in the payment instruction
   */
  MT202 convertToSwift(PaymentInstructions pi) {
    MT202 mt = new MT202(pi.payerDetails.bic, pi.payeeDetails.bic);
    mt.addField(new Field20().setReference(pi.transactionReference));
    mt.addField(new Field21().setReference(pi.transactionReference));
    mt.addField(
        new Field32A()
            .setAmount(pi.amount)
            .setDate(
                DATE_TIME_FORMATTER.format(pi.paymentDate.atOffset(ZoneOffset.UTC).toLocalDate()))
            .setCurrency(pi.currency));
    mt.addField(new Field58A().setAccount(pi.payeeDetails.iban).setBIC(pi.payeeDetails.bic));
    return mt;
  }
}
