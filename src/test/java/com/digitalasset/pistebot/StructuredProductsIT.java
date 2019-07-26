package com.digitalasset.pistebot;

import com.daml.ledger.javaapi.data.Party;
import com.digitalasset.testing.junit4.Sandbox;
import com.digitalasset.testing.utils.ContractWithId;
import da.refapps.structuredproducts.dcn.Trade;
import da.refapps.structuredproducts.dcn.TradeProposal;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class StructuredProductsIT {
        private static final Path RELATIVE_DAR_PATH =
                Paths.get("./target/structured-products.dar");
        private static final Integer sandboxPort = 6865;
        private static final int WAIT_TIMEOUT = 20;
        private static final String TEST_MODULE = "DA.RefApps.StructuredProducts.MarketSetup";
        private static final String TEST_SCENARIO = "marketSetup";

        private static Party INTERMEDIARY_PARTY = new Party("Intermediary");
        private static Party CLIENT_PARTY = new Party("Client");

        private static Sandbox sandboxC =
                Sandbox.builder()
                        .dar(RELATIVE_DAR_PATH)
                        .projectDir(Paths.get("."))
                        .module(TEST_MODULE)
                        .scenario(TEST_SCENARIO)
                        .parties(INTERMEDIARY_PARTY.getValue(), CLIENT_PARTY.getValue())
                        .setupAppCallback(client -> Main.runBots("localhost",
                                                                 sandboxPort,
                                                        "./output_messages"))
                        .build();

        @ClassRule
        public static ExternalResource compile = sandboxC.compilation();
        @Rule
        public Sandbox.Process sandbox = sandboxC.process();

        @Test
        public void testWorkflow() {
          TradeProposal.ContractId tradeProposal =
                  sandbox.getCreatedContractId(INTERMEDIARY_PARTY,
                                               TradeProposal.TEMPLATE_ID,
                                               TradeProposal.ContractId::new);
          sandbox.getLedgerAdapter()
                  .exerciseChoice(INTERMEDIARY_PARTY,
                                  tradeProposal.exerciseAccept("INTER001", "INTXXXABC", "1234567"));
          Trade.ContractId trade =
                sandbox.getCreatedContractId(INTERMEDIARY_PARTY,
                        Trade.TEMPLATE_ID,
                        Trade.ContractId::new);
          sandbox.getLedgerAdapter()
                .exerciseChoice(INTERMEDIARY_PARTY,
                        trade.exerciseProposeTradeToClient(CLIENT_PARTY.getValue(), BigDecimal.valueOf(26000000)));

          // Check the trade proposal with the client.
          ContractWithId<TradeProposal.ContractId> clientTrade =
                  sandbox.getMatchedContract(CLIENT_PARTY,
                          TradeProposal.TEMPLATE_ID,
                          TradeProposal.ContractId::new);
          assertEquals(26000000L, TradeProposal.fromValue(clientTrade.record).salesPrice.toBigInteger().longValue());
          sandbox.getLedgerAdapter()
                    .exerciseChoice(CLIENT_PARTY,
                            clientTrade.contractId.exerciseAccept("CLIENT001", "CLIXXXABC", "5678910"));

        }
}
