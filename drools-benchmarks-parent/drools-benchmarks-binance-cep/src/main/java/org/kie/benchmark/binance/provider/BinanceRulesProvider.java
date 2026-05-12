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
import org.kie.api.conf.MultithreadEvaluationOption;
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

    private static final String DEFAULT_RULES_PATH = "/rules/taxonomy.drl";
    private final String rulesPath;
    private final KieContainer kieContainer;

    /** Sequential (baseline) session factory. */
    public BinanceRulesProvider() {
        this(System.getProperty("binance.rules.file", DEFAULT_RULES_PATH), "baseline");
    }

    /**
     * Session factory configured for the given execution mode.
     *
     * @param mode one of {@code "baseline"}, {@code "PARALLEL_EVALUATION"}, {@code "FULLY_PARALLEL"}
     */
    public BinanceRulesProvider(String mode) {
        this(System.getProperty("binance.rules.file", DEFAULT_RULES_PATH), mode);
    }

    /**
     * Initialize with specific rules file path and execution mode.
     */
    public BinanceRulesProvider(String rulesPath, String mode) {
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

        // Configure STREAM mode so @expires annotations work
        // Parallel mode is handled at KieBase level via MultithreadEvaluationOption
        String multithreadAttr = ("PARALLEL_EVALUATION".equals(mode) || "FULLY_PARALLEL".equals(mode))
                ? " multithread=\"true\"" : "";
        if ("FULLY_PARALLEL".equals(mode)) System.setProperty("drools.parallelAgenda", "true");
        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n"
                + "  <kbase name=\"binanceBase\" eventProcessingMode=\"stream\""
                + multithreadAttr + ">\n"
                + "    <ksession name=\"binanceSession\"/>\n"
                + "  </kbase>\n"
                + "</kmodule>";
        kfs.write("src/main/resources/META-INF/kmodule.xml", kmoduleXml);

        // Build KieBase
        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
        kieBuilder.buildAll();

        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Rule compilation errors: " +
                    kieBuilder.getResults().getMessages(Message.Level.ERROR));
        }

        this.kieContainer = kieServices.newKieContainer(
                kieServices.getRepository().getDefaultReleaseId());

        System.out.printf("Loaded rules from: %s [mode=%s]%n", this.rulesPath, mode);
    }

    /**
     * Create a new KieSession with SessionPseudoClock.
     * This is the session type used for CEP benchmarks.
     */
    public KieSession createSession() {
        org.kie.api.runtime.KieSessionConfiguration config = org.kie.api.KieServices.Factory.get()
                .newKieSessionConfiguration();
        config.setOption(ClockTypeOption.PSEUDO);
        return kieContainer.newKieSession("binanceSession", config);
    }

    /**
     * Create a new KieSession with explicit clock type.
     */
    public KieSession createSession(ClockTypeOption clockType) {
        org.kie.api.runtime.KieSessionConfiguration config = org.kie.api.KieServices.Factory.get()
                .newKieSessionConfiguration();
        config.setOption(clockType);
        return kieContainer.newKieSession("binanceSession", config);
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
