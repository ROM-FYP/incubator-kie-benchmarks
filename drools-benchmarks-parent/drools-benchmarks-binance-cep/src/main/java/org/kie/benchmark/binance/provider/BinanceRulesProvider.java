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

package org.kie.benchmark.binance.provider;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.conf.ClockTypeOption;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Provides KieBase and KieSession for Binance benchmarks.
 * Loads the taxonomy.drl rules from resources.
 */
public class BinanceRulesProvider {

    private static final String DEFAULT_RULES_PATH = "/rules/demo.drl";
    private final String rulesPath;
    private final KieContainer kieContainer;

    /**
     * Initialize and build KieBase from rules file.
     * Uses system property 'binance.rules.file' or defaults to demo.drl.
     */
    public BinanceRulesProvider() {
        this(System.getProperty("binance.rules.file", DEFAULT_RULES_PATH));
    }

    /**
     * Initialize with specific rules file path.
     */
    public BinanceRulesProvider(String rulesPath) {
        this.rulesPath = rulesPath;
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();

        // Load rules from classpath
        try (InputStream is = getClass().getResourceAsStream(this.rulesPath)) {
            if (is == null) {
                throw new RuntimeException("Rules file not found: " + this.rulesPath);
            }

            String drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            kfs.write("src/main/resources" + this.rulesPath, drlContent);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load rules: " + this.rulesPath, e);
        }

        // Build KieBase
        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
        kieBuilder.buildAll();

        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Rule compilation errors: " +
                    kieBuilder.getResults().getMessages(Message.Level.ERROR));
        }

        this.kieContainer = kieServices.newKieContainer(
                kieServices.getRepository().getDefaultReleaseId());

        System.out.println("Loaded rules from: " + this.rulesPath);
    }

    /**
     * Create a new KieSession with SessionPseudoClock.
     * This is the session type used for CEP benchmarks.
     */
    public KieSession createSession() {
        org.kie.api.runtime.KieSessionConfiguration config = org.kie.api.KieServices.Factory.get()
                .newKieSessionConfiguration();
        config.setOption(ClockTypeOption.PSEUDO);
        return kieContainer.newKieSession(config);
    }

    /**
     * Create a new KieSession with explicit clock type.
     */
    public KieSession createSession(ClockTypeOption clockType) {
        org.kie.api.runtime.KieSessionConfiguration config = org.kie.api.KieServices.Factory.get()
                .newKieSessionConfiguration();
        config.setOption(clockType);
        return kieContainer.newKieSession(config);
    }

    /**
     * Get the KieContainer for advanced usage.
     */
    public KieContainer getKieContainer() {
        return kieContainer;
    }

    /**
     * Dispose of the KieContainer and release resources.
     */
    public void dispose() {
        if (kieContainer != null) {
            kieContainer.dispose();
        }
    }
}
