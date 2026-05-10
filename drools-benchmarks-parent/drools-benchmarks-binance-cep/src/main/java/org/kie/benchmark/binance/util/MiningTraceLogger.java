/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kie.benchmark.binance.util;

import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * A Drools agenda event listener that logs every rule firing to a CSV file
 * for process mining analysis.
 *
 * <p>
 * CSV columns: CaseID, SequenceNr, Activity, Timestamp
 * </p>
 *
 * <p>
 * Usage pattern:
 * <ol>
 * <li>Create once per trial: {@code new MiningTraceLogger("trace.csv")}</li>
 * <li>Register on each new KieSession:
 * {@code kieSession.addEventListener(logger)}</li>
 * <li>Call {@link #startNewTransaction()} before each benchmark invocation</li>
 * <li>Close once at trial teardown: {@link #close()}</li>
 * </ol>
 * </p>
 */
public class MiningTraceLogger extends DefaultAgendaEventListener {

    private BufferedWriter writer;
    private long transactionId = 0;
    private int sequenceCounter = 0;

    public MiningTraceLogger(String filePath) {
        try {
            writer = new BufferedWriter(new FileWriter(filePath));
            writer.write("CaseID,SequenceNr,Activity,Timestamp\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create trace log file: " + filePath, e);
        }
    }

    /**
     * Call before each benchmark invocation to mark a new logical transaction.
     * Each invocation gets a unique CaseID in the CSV.
     */
    public void startNewTransaction() {
        transactionId++;
        sequenceCounter = 0;
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        try {
            sequenceCounter++;
            String ruleName = event.getMatch().getRule().getName();
            long timestamp = System.currentTimeMillis();

            writer.write(String.format("%d,%d,%s,%d\n",
                    transactionId,
                    sequenceCounter,
                    ruleName.replace(",", " "),
                    timestamp));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write trace log entry", e);
        }
    }

    /**
     * Flush and close the underlying writer. Call once at trial teardown.
     */
    public void close() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to close trace log file", e);
        }
    }
}
