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

package org.kie.benchmark.cep.wikimedia;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Factory for creating Drools KieSession configured for CEP with STREAM mode.
 */
public class CepSessionFactory {
    private static final Logger logger = LoggerFactory.getLogger(CepSessionFactory.class);

    private final KieBase kieBase;

    public CepSessionFactory(String rulesPath) {
        this.kieBase = createKieBase(rulesPath);
    }

    private KieBase createKieBase(String rulesPath) {
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kfs = kieServices.newKieFileSystem();

            // Load rules from classpath
            InputStream rulesStream = getClass().getClassLoader().getResourceAsStream(rulesPath);
            if (rulesStream == null) {
                throw new RuntimeException("Cannot find rules file: " + rulesPath);
            }

            kfs.write("src/main/resources/" + rulesPath,
                    ResourceFactory.newInputStreamResource(rulesStream));

            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
            kieBuilder.buildAll();

            if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                throw new RuntimeException("Build errors: " + kieBuilder.getResults().toString());
            }

            // Create KieBase with STREAM mode for CEP
            org.kie.api.KieBaseConfiguration kieBaseConfig = kieServices.newKieBaseConfiguration();
            kieBaseConfig.setOption(EventProcessingOption.STREAM);
            
            return kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId())
                    .newKieBase(kieBaseConfig);


        } catch (Exception e) {
            logger.error("Failed to create KieBase", e);
            throw new RuntimeException("Failed to create KieBase", e);
        }
    }

    /**
     * Create a new stateful KieSession.
     */
    public KieSession createSession() {
        return createSession(false);
    }
    
    public KieSession createSession(boolean pseudoClock) {
        KieServices kieServices = KieServices.Factory.get();
        org.kie.api.runtime.KieSessionConfiguration sessionConfig = kieServices.newKieSessionConfiguration();
        
        if (pseudoClock) {
            sessionConfig.setOption(org.kie.api.runtime.conf.ClockTypeOption.get("pseudo"));
        }
        
        return kieBase.newKieSession(sessionConfig, null);
    }
}
