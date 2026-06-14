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
package org.kie.benchmark.cep.riperis.parallel;

import org.kie.benchmark.cep.riperis.model.RisMessage;

/**
 * Routes each {@link RisMessage} to exactly one cluster based on the
 * alpha-filter constraints of the rules consuming {@code BgpUpdateFact}.
 *
 * <p>Routing logic mirrors the LHS constraints of the announcement/withdrawal
 * entry-point rules (019 and 020) on the {@code BgpUpdateFact} produced from
 * each {@code RisMessage}:
 *
 * <ul>
 *   <li>{@link ClusterId#C2_ANNOUNCEMENT}: events where {@code announcements} is non-empty
 *       (includes mixed announcement + withdrawal events).</li>
 *   <li>{@link ClusterId#C3_WITHDRAWAL}: BGP UPDATE events containing announcements OR
 *       withdrawals, required to ensure temporal rules 078/079 can join withdrawals
 *       and announcements.</li>
 *   <li>{@link ClusterId#C1_GENERAL}: all other events (non-UPDATE, header-only)</li>
 * </ul>
 */
import java.util.List;
import java.util.ArrayList;

public class RipeRisEventRouter {

    private RipeRisEventRouter() {
        // Utility class
    }

    /**
     * Determines which clusters should handle this event.
     * Returns a list of {@link ClusterId} targets.
     *
     * @param event the incoming RisMessage
     * @return the target cluster IDs
     */
    public static List<ClusterId> route(RisMessage event) {
        List<ClusterId> targets = new ArrayList<>();
        
        boolean hasAnnouncements = event.getAnnouncements() != null
                && !event.getAnnouncements().isEmpty();
        boolean hasWithdrawals = event.getWithdrawals() != null
                && !event.getWithdrawals().isEmpty();

        // C1: General / Non-UPDATE — header-only or non-BGP-UPDATE events
        if (!hasAnnouncements && !hasWithdrawals) {
            targets.add(ClusterId.C1_GENERAL);
            return targets;
        }

        // C2: Announcement pipeline — any event carrying route announcements
        if (hasAnnouncements) {
            targets.add(ClusterId.C2_ANNOUNCEMENT);
        }

        // C3: Withdrawal pipeline — all BGP UPDATE events (since C3's temporal rules
        // join RouteWithdrawal and RouteAnnouncement, C3 must see all updates)
        if (hasAnnouncements || hasWithdrawals) {
            targets.add(ClusterId.C3_WITHDRAWAL);
        }

        return targets;
    }
}
