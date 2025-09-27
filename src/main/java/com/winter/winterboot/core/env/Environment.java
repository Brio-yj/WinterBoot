package com.winter.winterboot.core.env;

import java.io.IOException;
import java.util.Properties;

public class Environment {
    private final Properties props = new Properties();

    public Environment() {
        try (var in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }
    public int getInt(String key, int def) {
        String v = props.getProperty(key);
        return (v == null) ? def : Integer.parseInt(v);
    }
    public boolean getBool(String key, boolean def) {
        String v = props.getProperty(key);
        return (v == null) ? def : Boolean.parseBoolean(v);
    }

    public String getRaw(String key) { return props.getProperty(key); }
    public boolean hasKey(String key) { return props.containsKey(key); }
}
