package org.kie.benchmark.cep.reproducible.wikimedia.drools;

import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DroolsSessionFactory {

    private DroolsSessionFactory() {}

    public static KieSession fromDrl(Path drlPath) throws Exception {
        KieServices ks = KieServices.Factory.get();

        org.kie.api.builder.model.KieModuleModel kmm = ks.newKieModuleModel();
        org.kie.api.builder.model.KieBaseModel kbm = kmm.newKieBaseModel("defaultKieBase")
                .setDefault(true)
                .setEventProcessingMode(org.kie.api.conf.EventProcessingOption.STREAM);
        kbm.newKieSessionModel("defaultKieSession")
                .setDefault(true);

        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.writeKModuleXML(kmm.toXML());
        kfs.write("src/main/resources/reproducible/wikimedia/rules/advanced_viral_rules.drl",
                Files.readString(drlPath));

        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();

        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException(
                "DRL errors:\n" + results.getMessages(Message.Level.ERROR)
            );
        }

        KieContainer kc = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
        return kc.newKieSession();
    }
}
