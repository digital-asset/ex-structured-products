/*
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.pistebot;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.daml.ledger.javaapi.data.Identifier;
import com.daml.ledger.javaapi.data.Party;
import com.daml.ledger.javaapi.data.Value;
import com.digitalasset.testing.junit4.Sandbox;
import com.digitalasset.testing.ledger.DefaultLedgerAdapter;
import com.digitalasset.testing.utils.ContractWithId;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import da.refapps.structuredproducts.dcn.CouponEvent;
import da.refapps.structuredproducts.dcn.IntermediaryTradingRole;
import da.refapps.structuredproducts.dcn.KnockOutEvent;
import da.refapps.structuredproducts.dcn.MarketData;
import da.refapps.structuredproducts.dcn.PaymentInstructions;
import da.refapps.structuredproducts.dcn.Trade;
import da.refapps.structuredproducts.dcn.TradeProposal;
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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class StructuredProductsIT {
  private static final Path RELATIVE_DAR_PATH = Paths.get("./target/structured-products.dar");
  private static final String TEST_MODULE = "DA.RefApps.StructuredProducts.MarketSetup";
  private static final String TEST_SCRIPT = "marketSetup";

  private static final Party INTERMEDIARY_PARTY = new Party("Intermediary");
  private static final Party CLIENT_PARTY = new Party("Client");
  private static final Party ISSUER_PARTY = new Party("Issuer");

  private static final List<String> telegramMessages = new ArrayList<>();

  private static final Sandbox sandbox =
      Sandbox.builder()
          .dar(RELATIVE_DAR_PATH)
          .module(TEST_MODULE)
          .startScript(TEST_SCRIPT)
          .parties(INTERMEDIARY_PARTY.getValue(), CLIENT_PARTY.getValue(), ISSUER_PARTY.getValue())
          .setupAppCallback(
              client -> Main.runBots(client, "./output_messages", telegramMessages::add))
          .build();

  @ClassRule public static ExternalResource sandboxClassRule = sandbox.getClassRule();
  @Rule public ExternalResource sandboxRule = sandbox.getRule();

  @Before
  public void setUp() {
    telegramMessages.clear();
  }

  @Test
  public void testWorkflow() throws InvalidProtocolBufferException {
    final DefaultLedgerAdapter ledgerAdapter = sandbox.getLedgerAdapter();
    TradeProposal.ContractId tradeProposal =
        ledgerAdapter.getCreatedContractId(
            INTERMEDIARY_PARTY, TradeProposal.TEMPLATE_ID, TradeProposal.ContractId::new);
    ledgerAdapter.exerciseChoice(
        INTERMEDIARY_PARTY, tradeProposal.exerciseAccept("INTER001", "INTXXXABC", "1234567"));
    Trade.ContractId tradeCid =
        ledgerAdapter.getCreatedContractId(
            INTERMEDIARY_PARTY, Trade.TEMPLATE_ID, Trade.ContractId::new);
    IntermediaryTradingRole.ContractId tradingRoleCid =
        ledgerAdapter.getCreatedContractId(
            INTERMEDIARY_PARTY,
            IntermediaryTradingRole.TEMPLATE_ID,
            IntermediaryTradingRole.ContractId::new);
    ledgerAdapter.exerciseChoice(
        INTERMEDIARY_PARTY,
        tradingRoleCid.exerciseProposeTradeToClient(
            CLIENT_PARTY.getValue(), BigDecimal.valueOf(26000000), tradeCid));
    // Check the trade proposal with the client.
    ContractWithId<TradeProposal.ContractId> clientTrade =
        ledgerAdapter.getMatchedContract(
            CLIENT_PARTY, TradeProposal.TEMPLATE_ID, TradeProposal.ContractId::new);
    assertEquals(
        26000000L,
        TradeProposal.fromValue(clientTrade.record).salesPrice.toBigInteger().longValue());
    ledgerAdapter.exerciseChoice(
        CLIENT_PARTY, clientTrade.contractId.exerciseAccept("CLIENT001", "CLIXXXABC", "5678910"));

    Trade.ContractId clientTradeCid =
        ledgerAdapter.getCreatedContractId(
            INTERMEDIARY_PARTY, Trade.TEMPLATE_ID, Trade.ContractId::new);

    LocalDate date1 = LocalDate.of(2019, 11, 11);
    LocalDate date2 = LocalDate.of(2022, 2, 8);
    ledgerAdapter.setCurrentTime(Instant.ofEpochSecond(date1.toEpochDay() * 24 * 60 * 60));

    // get market data
    MarketData.ContractId marketData2019Cid =
        find(
            ledgerAdapter,
            ISSUER_PARTY,
            MarketData.TEMPLATE_ID,
            MarketData.ContractId::new,
            MarketData::fromValue,
            m -> m.publishDate.atZone(ZoneOffset.UTC).toLocalDate().equals(date1));
    MarketData.ContractId marketData2022Cid =
        find(
            ledgerAdapter,
            ISSUER_PARTY,
            MarketData.TEMPLATE_ID,
            MarketData.ContractId::new,
            MarketData::fromValue,
            m -> m.publishDate.atZone(ZoneOffset.UTC).toLocalDate().equals(date2));

    // Coupon Event: Issuer vs. Intermediary
    ledgerAdapter.exerciseChoice(ISSUER_PARTY, tradeCid.exerciseLifecycle(marketData2019Cid));
    // check if event created
    ledgerAdapter.getCreatedContractId(
        ISSUER_PARTY, CouponEvent.TEMPLATE_ID, CouponEvent.ContractId::new);

    // Coupon Event: Intermediary vs. Client
    ledgerAdapter.exerciseChoice(
        INTERMEDIARY_PARTY, clientTradeCid.exerciseLifecycle(marketData2019Cid));
    ledgerAdapter.setCurrentTime(Instant.ofEpochSecond(date2.toEpochDay() * 24 * 60 * 60));

    assertTrue(
        ledgerAdapter.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            PaymentInstructions.TEMPLATE_ID,
            PaymentInstructions::fromValue,
            false,
            i -> i.transactionReference.equals("CLIENT001")));
    assertTrue(
        ledgerAdapter.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            CouponEvent.TEMPLATE_ID,
            CouponEvent::fromValue,
            false,
            e -> e.tradeId.equals("CLIENT001")));

    // Knockout Event: Issuer vs. Intermediary
    ledgerAdapter.exerciseChoice(ISSUER_PARTY, tradeCid.exerciseLifecycle(marketData2022Cid));
    ledgerAdapter.getCreatedContractId(
        ISSUER_PARTY, KnockOutEvent.TEMPLATE_ID, KnockOutEvent.ContractId::new);

    // Knockout Event: Intermediary vs. Client
    ledgerAdapter.exerciseChoice(
        INTERMEDIARY_PARTY, clientTradeCid.exerciseLifecycle(marketData2022Cid));
    assertTrue(
        ledgerAdapter.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            KnockOutEvent.TEMPLATE_ID,
            KnockOutEvent::fromValue,
            false,
            e -> e.tradeId.equals("CLIENT001")));
    assertTrue(
        ledgerAdapter.observeMatchingContracts(
            INTERMEDIARY_PARTY,
            PaymentInstructions.TEMPLATE_ID,
            PaymentInstructions::fromValue,
            false,
            i -> i.transactionReference.equals("CLIENT001")));

    // check Telegram messages are sent
    List<String> expectedTelegramMessages = generateExpectedTelegramMessages();
    for (int i = 0; i < telegramMessages.size(); ++i) {
      assertThat(telegramMessages.get(i), startsWith(expectedTelegramMessages.get(i)));
    }
    // Assert size after messages so in case of a failure we get more descriptive error message,
    // than for example 7 was not 8.
    assertThat(telegramMessages.size(), is(expectedTelegramMessages.size()));
  }

  private <Cid, Contract> Cid find(
      DefaultLedgerAdapter ledgerAdapter,
      Party party,
      Identifier id,
      Function<String, Cid> idFactory,
      Function<Value, Contract> ctor,
      Predicate<Contract> predicate) {
    while (true) {
      ContractWithId<Cid> matchedContract = ledgerAdapter.getMatchedContract(party, id, idFactory);
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
