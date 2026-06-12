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

/**
 * Identifies each parallel rule cluster in the RIPE RIS CEP partition architecture.
 */
public enum ClusterId {

    /** C1: General / Non-UPDATE validation. Receives events with no announcement or withdrawal payload. */
    C1_GENERAL,

    /** C2: Announcement pipeline. Receives events with a non-empty announcements list (including mixed). */
    C2_ANNOUNCEMENT,

    /** C3: Withdrawal pipeline. Receives events with a non-empty withdrawals list (and no announcements). */
    C3_WITHDRAWAL
}
