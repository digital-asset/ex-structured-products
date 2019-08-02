/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.pistebot;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import com.daml.ledger.javaapi.data.Identifier;
import com.daml.ledger.javaapi.data.Party;
import com.daml.ledger.javaapi.data.Value;
import com.digitalasset.testing.junit4.Sandbox;
import com.digitalasset.testing.utils.ContractWithId;
import com.google.common.collect.Lists;
import da.refapps.structuredproducts.dcn.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class StructuredProductsIT {
  private static final Path RELATIVE_DAR_PATH = Paths.get("./target/structured-products.dar");
  private static final Integer sandboxPort = 6865;
  private static final String TEST_MODULE = "DA.RefApps.StructuredProducts.MarketSetup";
  private static final String TEST_SCENARIO = "marketSetup";

  private static Party INTERMEDIARY_PARTY = new Party("Intermediary");
  private static Party CLIENT_PARTY = new Party("Client");
  private static Party ISSUER_PARTY = new Party("Issuer");

  private static List<String> telegramMessages = new ArrayList<>();

  private static Sandbox sandboxC =
      Sandbox.builder()
          .dar(RELATIVE_DAR_PATH)
          .projectDir(Paths.get("."))
          .module(TEST_MODULE)
          .scenario(TEST_SCENARIO)
          .parties(INTERMEDIARY_PARTY.getValue(), CLIENT_PARTY.getValue(), ISSUER_PARTY.getValue())
          .setupAppCallback(
              client ->
                  Main.runBots(
                      "localhost", sandboxPort, "./output_messages", telegramMessages::add))
          .build();

  @ClassRule public static ExternalResource compile = sandboxC.compilation();
  @Rule public Sandbox.Process sandbox = sandboxC.process();

  @Test
  public void testWorkflow() {
    TradeProposal.ContractId tradeProposal =
        sandbox.getCreatedContractId(
            INTERMEDIARY_PARTY, TradeProposal.TEMPLATE_ID, TradeProposal.ContractId::new);
    sandbox
        .getLedgerAdapter()
        .exerciseChoice(
            INTERMEDIARY_PARTY, tradeProposal.exerciseAccept("INTER001", "INTXXXABC", "1234567"));
    Trade.ContractId tradeCid =
        sandbox.getCreatedContractId(INTERMEDIARY_PARTY, Trade.TEMPLATE_ID, Trade.ContractId::new);
    sandbox
        .getLedgerAdapter()
        .exerciseChoice(
            INTERMEDIARY_PARTY,
            tradeCid.exerciseProposeTradeToClient(
                CLIENT_PARTY.getValue(), BigDecimal.valueOf(26000000)));

    // Check the trade proposal with the client.
    ContractWithId<TradeProposal.ContractId> clientTrade =
        sandbox.getMatchedContract(
            CLIENT_PARTY, TradeProposal.TEMPLATE_ID, TradeProposal.ContractId::new);
    assertEquals(
        26000000L,
        TradeProposal.fromValue(clientTrade.record).salesPrice.toBigInteger().longValue());
    sandbox
        .getLedgerAdapter()
        .exerciseChoice(
            CLIENT_PARTY,
            clientTrade.contractId.exerciseAccept("CLIENT001", "CLIXXXABC", "5678910"));

    Trade.ContractId clientTradeCid =
        sandbox.getCreatedContractId(INTERMEDIARY_PARTY, Trade.TEMPLATE_ID, Trade.ContractId::new);

    LocalDate date1 = LocalDate.of(2019, 11, 11);
    LocalDate date2 = LocalDate.of(2022, 2, 8);
    sandbox
        .getLedgerAdapter()
        .setCurrentTime(Instant.ofEpochSecond(date1.toEpochDay() * 24 * 60 * 60));

    // get market data
    MarketData.ContractId marketData2019Cid =
        find(
            ISSUER_PARTY,
            MarketData.TEMPLATE_ID,
            MarketData.ContractId::new,
            MarketData::fromValue,
            m -> m.publishDate.atZone(ZoneOffset.UTC).toLocalDate().equals(date1));
    MarketData.ContractId marketData2022Cid =
        find(
            ISSUER_PARTY,
            MarketData.TEMPLATE_ID,
            MarketData.ContractId::new,
            MarketData::fromValue,
            m -> m.publishDate.atZone(ZoneOffset.UTC).toLocalDate().equals(date2));

    // Coupon Event: Issuer vs. Intermediary
    sandbox
        .getLedgerAdapter()
        .exerciseChoice(ISSUER_PARTY, tradeCid.exerciseLifecycle(marketData2019Cid));
    // check if event created
    sandbox.getCreatedContractId(
        ISSUER_PARTY, CouponEvent.TEMPLATE_ID, CouponEvent.ContractId::new);

    // Coupon Event: Intermediary vs. Client
    sandbox
        .getLedgerAdapter()
        .exerciseChoice(INTERMEDIARY_PARTY, clientTradeCid.exerciseLifecycle(marketData2019Cid));
    sandbox
        .getLedgerAdapter()
        .setCurrentTime(Instant.ofEpochSecond(date2.toEpochDay() * 24 * 60 * 60));

    assertTrue(
        sandbox.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            PaymentInstructions.TEMPLATE_ID,
            PaymentInstructions::fromValue,
            false,
            i -> i.transactionReference.equals("CLIENT001")));
    assertTrue(
        sandbox.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            CouponEvent.TEMPLATE_ID,
            CouponEvent::fromValue,
            false,
            e -> e.tradeId.equals("CLIENT001")));

    // Knockout Event: Issuer vs. Intermediary
    sandbox
        .getLedgerAdapter()
        .exerciseChoice(ISSUER_PARTY, tradeCid.exerciseLifecycle(marketData2022Cid));
    sandbox.getCreatedContractId(
        ISSUER_PARTY, KnockOutEvent.TEMPLATE_ID, KnockOutEvent.ContractId::new);

    // Knockout Event: Intermediary vs. Client
    sandbox
        .getLedgerAdapter()
        .exerciseChoice(INTERMEDIARY_PARTY, clientTradeCid.exerciseLifecycle(marketData2022Cid));
    assertTrue(
        sandbox.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            KnockOutEvent.TEMPLATE_ID,
            KnockOutEvent::fromValue,
            false,
            e -> e.tradeId.equals("CLIENT001")));
    assertTrue(
        sandbox.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            PaymentInstructions.TEMPLATE_ID,
            PaymentInstructions::fromValue,
            false,
            i -> i.transactionReference.equals("CLIENT001")));

    // check Telegram messages are sent
    List<String> expectedTelegramMessages = generateExpectedTelegramMessages();
    assertThat(telegramMessages.size(), is(expectedTelegramMessages.size()));
    for (int i = 0; i < telegramMessages.size(); ++i) {
      assertThat(telegramMessages.get(i), startsWith(expectedTelegramMessages.get(i)));
    }
  }

  private <Cid, Contract> Cid find(
      Party party,
      Identifier id,
      Function<String, Cid> idFactory,
      Function<Value, Contract> ctor,
      Predicate<Contract> predicate) {
    while (true) {
      ContractWithId<Cid> matchedContract = sandbox.getMatchedContract(party, id, idFactory);
      Contract contract = ctor.apply(matchedContract.record);
      if (predicate.test(contract)) {
        return matchedContract.contractId;
      }
    }
  }

  private List<String> generateExpectedTelegramMessages() {
    return Lists.newArrayList(
        "Coupon event occurred on trade INTER001 between Issuer and Intermediary",
        "SWIFT transfer initiated from ISSUERWCHHK80A to beneficiary INTXXXABXC for 625000 JPY on 191118 (ref=INTER001, id=", // id is changing on each run
        "Coupon event occurred on trade CLIENT001 between Intermediary and Client",
        "SWIFT transfer initiated from INTXXXABAC to beneficiary CLIXXXABXC for 325000 JPY on 191118 (ref=CLIENT001, id=",
        "DCN INTER001 has knocked out, reason:  Closing Prices (NKY: 22057.3 JPY, INDU: 25402.47 USD) exceeded Knock-Out Prices (NKY: 21687.65 JPY, INDU: 25380.74 USD on Knock-Out Determination Date 2022-02-08",
        "SWIFT transfer initiated from ISSUERWCHHK80A to beneficiary INTXXXABXC for 50000000 JPY on 220216 (ref=INTER001, id=",
        "DCN CLIENT001 has knocked out, reason:  Closing Prices (NKY: 22057.3 JPY, INDU: 25402.47 USD) exceeded Knock-Out Prices (NKY: 21687.65 JPY, INDU: 25380.74 USD on Knock-Out Determination Date 2022-02-08",
        "SWIFT transfer initiated from INTXXXABAC to beneficiary CLIXXXABXC for 26000000 JPY on 220216 (ref=CLIENT001, id=");
  }
}
