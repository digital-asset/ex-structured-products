/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.ledger;

import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.DamlLedgerClient;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.internal.operators.flowable.FlowableFromIterable;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LedgerAPI {
  private static final Logger logger = LoggerFactory.getLogger(LedgerAPI.class);

  private DamlLedgerClient ledgerClient;
  private final String applicationId;
  private final String hostname;
  private final int port;
  private final CompositeDisposable compositeDisposable = new CompositeDisposable();

  public LedgerAPI(String hostname, int port) {
    this.hostname = hostname;
    this.port = port;
    this.applicationId = "Func-com.digitalasset.piste.lifecyclebot.LifecycleBotOld-Framework";
  }

  public void start() {
    ledgerClient = DamlLedgerClient.forHostWithLedgerIdDiscovery(hostname, port, Optional.empty());

    boolean connected = false;
    while (!connected) {
      try {
        ledgerClient.connect();
        connected = true;
        logger.info(String.format("Connected to sandbox at %s:%s", hostname, port));
      } catch (Exception ignored) {
        logger.info(String.format("Connecting to sandbox at %s:%s", hostname, port));
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignored2) {
        }
      }
    }
  }

  public void stop() {
    compositeDisposable.dispose();
    try {
      ledgerClient.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Optional<String> discoverPackageId() {
    return Optional.of(ledgerClient.getPackageClient().listPackages().firstElement().blockingGet());
  }

  public void executeLedgerCommand(String partyName, Command command) {
    final String commandUUID = UUID.randomUUID().toString();
    ledgerClient
        .getCommandClient()
        .submitAndWait(
            UUID.randomUUID().toString(),
            applicationId,
            commandUUID,
            partyName,
            Instant.now(),
            Instant.now().plusSeconds(10),
            Collections.singletonList(command))
        .blockingGet();
  }

  public void listenEvents(String partyName, Consumer<Event> process) {
    Flowable<Event> events =
        ledgerClient
            .getTransactionsClient()
            .getTransactions(
                LedgerOffset.LedgerBegin.getInstance(),
                new FiltersByParty(Collections.singletonMap(partyName, NoFilter.instance)),
                true)
            .flatMap(tx -> new FlowableFromIterable<>(tx.getEvents()));
    compositeDisposable.add(events.forEach(process::accept));
  }
}
