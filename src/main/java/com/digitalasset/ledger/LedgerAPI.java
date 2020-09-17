/*
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.ledger;

import com.daml.ledger.javaapi.data.Event;
import com.daml.ledger.javaapi.data.FiltersByParty;
import com.daml.ledger.javaapi.data.LedgerOffset;
import com.daml.ledger.javaapi.data.NoFilter;
import com.daml.ledger.rxjava.DamlLedgerClient;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.internal.operators.flowable.FlowableFromIterable;
import java.util.Collections;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LedgerAPI {
  private static final Logger logger = LoggerFactory.getLogger(LedgerAPI.class);

  private final DamlLedgerClient ledgerClient;
  private final CompositeDisposable compositeDisposable = new CompositeDisposable();

  public LedgerAPI(DamlLedgerClient client) {
    ledgerClient = client;
  }

  public void start() {
    boolean connected = false;
    while (!connected) {
      try {
        ledgerClient.connect();
        connected = true;
        logger.info("Connected to sandbox.");
      } catch (Exception ignored) {
        logger.info("Connecting to sandbox.");
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
