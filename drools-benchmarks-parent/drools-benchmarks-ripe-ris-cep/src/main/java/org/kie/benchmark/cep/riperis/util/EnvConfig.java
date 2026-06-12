package org.kie.benchmark.cep.riperis.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class EnvConfig {
    private static final Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(".env")) {
            props.load(fis);
        } catch (IOException e) {
            // Ignore if missing
        }
    }

    public static String get(String key) {
        String val = System.getenv(key);
        if (val != null) return val;
        return props.getProperty(key);
    }
}
