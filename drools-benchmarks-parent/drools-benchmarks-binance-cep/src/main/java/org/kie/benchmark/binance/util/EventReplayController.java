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

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.MarketEvent;
import org.drools.core.time.SessionPseudoClock;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controls event replay using SessionPseudoClock for deterministic timing.
 * Advances clock to each event's timestamp and inserts events in order.
 */
public class EventReplayController {

    private final KieSession kieSession;
    private final SessionPseudoClock sessionClock;

    public EventReplayController(KieSession kieSession) {
        this.kieSession = kieSession;
        this.sessionClock = kieSession.getSessionClock();
    }

    /**
     * Replay events in event-time order (sorted by exchange_ts).
     * Advances SessionPseudoClock to each event's timestamp before insertion.
     * 
     * @param events List of events sorted by exchange_ts
     * @return Total number of rule activations
     */
    public int replayEvents(List<MarketEvent> events) {
        if (events.isEmpty()) {
            return 0;
        }

        long startTime = events.get(0).getTsMs();
        int totalFired = 0;

        for (MarketEvent event : events) {
            // Advance clock to event time
            long eventTime = event.getTsMs();
            long currentTime = sessionClock.getCurrentTime();

            if (eventTime > currentTime) {
                sessionClock.advanceTime(eventTime - currentTime, TimeUnit.MILLISECONDS);
            }

            // Insert event and fire rules
            kieSession.insert(event);
            totalFired += kieSession.fireAllRules();
        }

        return totalFired;
    }

    /**
     * Replay events with max-speed (no pacing).
     * Same as replayEvents() but optimized for benchmarking.
     */
    public int replayEventsMaxSpeed(List<MarketEvent> events) {
        return replayEvents(events);
    }

    /**
     * Replay events with real-time pacing (1× speed).
     * Sleeps between events to simulate real arrival times.
     * 
     * @param events          List of events sorted by exchange_ts
     * @param speedMultiplier Speed multiplier (1.0 = real-time, 10.0 = 10× speed)
     * @return Total number of rule activations
     */
    public int replayEventsWithPacing(List<MarketEvent> events, double speedMultiplier) {
        if (events.isEmpty()) {
            return 0;
        }

        int totalFired = 0;
        long prevEventTime = events.get(0).getTsMs();

        for (MarketEvent event : events) {
            long eventTime = event.getTsMs();
            long delay = eventTime - prevEventTime;

            if (delay > 0 && speedMultiplier > 0) {
                try {
                    Thread.sleep((long) (delay / speedMultiplier));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Advance clock and insert
            sessionClock.advanceTime(delay, TimeUnit.MILLISECONDS);
            kieSession.insert(event);
            totalFired += kieSession.fireAllRules();

            prevEventTime = eventTime;
        }

        return totalFired;
    }

    /**
     * Get current session clock time.
     */
    public long getCurrentTime() {
        return sessionClock.getCurrentTime();
    }
}
