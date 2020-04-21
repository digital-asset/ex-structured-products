/*
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.pistebot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.daml.ledger.javaapi.data.CreatedEvent;
import com.prowidesoftware.swift.model.mt.mt2xx.MT202;
import da.refapps.structuredproducts.dcn.AccountDetails;
import da.refapps.structuredproducts.dcn.ClosingPrice;
import da.refapps.structuredproducts.dcn.CouponEvent;
import da.refapps.structuredproducts.dcn.DayCountFraction;
import da.refapps.structuredproducts.dcn.KnockOutEvent;
import da.refapps.structuredproducts.dcn.PaymentInstructions;
import da.refapps.structuredproducts.dcn.PriceAndCCY;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Test;

public class PisteBotTest {

  private static final String ISSUER = "issuer";
  private static final String OWNER = "owner";
  private static final String REGULATOR = "regulator";
  private static final String TRADE_ID = "tradeId";
  private static final String PRODUCT_ID = "productId";
  private static final String USD = "USD";
  private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<MT202> swiftMessages = new CopyOnWriteArrayList<>();
  private PisteBot bot;

  @Before
  public void setupBot() {
    bot = new PisteBot(msg -> messages.add(msg), swift -> swiftMessages.add(swift));
  }

  /** Checks if the expected outgoing Telegram message and no Swift message was generated */
  @Test
  public void testCouponEvent() {
    PriceAndCCY strike1 = new PriceAndCCY(BigDecimal.valueOf(100), USD);
    ClosingPrice priceIndex1 = new ClosingPrice("asset-1", strike1);
    PriceAndCCY strike2 = new PriceAndCCY(BigDecimal.valueOf(100), USD);
    ClosingPrice priceIndex2 = new ClosingPrice("asset-2", strike2);
    CouponEvent couponEvent =
        new CouponEvent(
            TRADE_ID,
            PRODUCT_ID,
            BigDecimal.valueOf(0.02),
            new DayCountFraction(1L, 2L),
            Instant.now(),
            strike1,
            priceIndex1,
            strike2,
            priceIndex2,
            ISSUER,
            OWNER,
            REGULATOR);

    CreatedEvent event =
        new CreatedEvent(
            Collections.emptyList(),
            "event-1",
            CouponEvent.TEMPLATE_ID,
            "cid-1",
            couponEvent.toValue(),
            Optional.empty(),
            Optional.empty(),
            Collections.emptyList(),
            Collections.emptyList());

    bot.accept(event);
    assertEquals(
        "Coupon event occurred on trade tradeId between issuer and owner", messages.get(0));
    assertEquals(0, swiftMessages.size());
  }

  /** Checks if the expected outgoing Telegram message and no Swift message was generated */
  @Test
  public void testKnockOutEvent() {
    PriceAndCCY index1KO = new PriceAndCCY(BigDecimal.valueOf(100), USD);
    ClosingPrice closingPriceIndex1 = new ClosingPrice("asset-1", index1KO);
    PriceAndCCY index2KO = new PriceAndCCY(BigDecimal.valueOf(100), USD);
    ClosingPrice closingPriceIndex2 = new ClosingPrice("asset-2", index2KO);
    KnockOutEvent knockOutEvent =
        new KnockOutEvent(
            TRADE_ID,
            PRODUCT_ID,
            Instant.now(),
            index1KO,
            closingPriceIndex1,
            index2KO,
            closingPriceIndex2,
            "Some reason for knock out",
            ISSUER,
            OWNER,
            REGULATOR);

    CreatedEvent event =
        new CreatedEvent(
            Collections.emptyList(),
            "event-1",
            KnockOutEvent.TEMPLATE_ID,
            "cid-1",
            knockOutEvent.toValue(),
            Optional.empty(),
            Optional.empty(),
            Collections.emptyList(),
            Collections.emptyList());

    bot.accept(event);
    assertEquals("DCN tradeId has knocked out, reason: Some reason for knock out", messages.get(0));
    assertEquals(0, swiftMessages.size());
  }

  private static final String EXPECTED_SWIFT_MESSAGE_PART1 =
      "{1:F01payerBicAXXX0000000000}{2:I202payeeBicXXXXN}";

  // swift messages use CRLF as line separator
  private static final String EXPECTED_SWIFT_MESSAGE_PART2 =
      "{4:\r\n" + ":20:txRefCode\r\n" + ":21:txRefCode\r\n" + ":32A";

  // swift messages use CRLF as line separator
  private static final String EXPECTED_SWIFT_MESSAGE_PART3 =
      "10,\r\n" + ":58A:/payeeIban\r\n" + "payeeBic\r\n" + "-}";

  /** Checks if the expected outgoing Telegram and Swift messages were generated */
  @Test
  public void testPaymentMessageEvent() {
    AccountDetails payerDetails = new AccountDetails("payer", "payerBic", "payerIban");
    AccountDetails payeeDetails = new AccountDetails("payee", "payeeBic", "payeeIban");
    PaymentInstructions paymentInstructions =
        new PaymentInstructions(
            payerDetails, payeeDetails, "txRefCode", BigDecimal.TEN, USD, Instant.now(), REGULATOR);

    CreatedEvent event =
        new CreatedEvent(
            Collections.emptyList(),
            "event-1",
            PaymentInstructions.TEMPLATE_ID,
            "cid-1",
            paymentInstructions.toValue(),
            Optional.empty(),
            Optional.empty(),
            Collections.emptyList(),
            Collections.emptyList());

    bot.accept(event);
    // last piece of the message is a volatile id (Unique End to End Transaction Reference)
    assertTrue(
        messages
            .get(0)
            .startsWith(
                "SWIFT transfer initiated from payerBicAXXX to beneficiary "
                    + "payeeBicXXXX for 10 USD on "
                    + new SimpleDateFormat("yyMMdd").format(Date.from(Instant.now()))
                    + " (ref=txRefCode, id="));
    // the the swift message contains the above id and the current date, so it is ignored from the
    // check
    String[] msgParts =
        swiftMessages.get(0).message().split("(\\{3:\\{121:[A-Za-z0-9-]*\\}\\})|(:[0-9]{6}USD)");
    assertEquals(3, msgParts.length);
    assertEquals(EXPECTED_SWIFT_MESSAGE_PART1, msgParts[0]);
    assertEquals(EXPECTED_SWIFT_MESSAGE_PART2, msgParts[1]);
    assertEquals(EXPECTED_SWIFT_MESSAGE_PART3, msgParts[2]);
  }
}
