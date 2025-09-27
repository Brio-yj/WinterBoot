package com.winter.winterboot.core.condition;

import com.winter.winterboot.core.env.Environment;

public final class ConditionEvaluator {
    private ConditionEvaluator() {}

    public static boolean matchesConditionalOnClass(ClassLoader cl, String[] classNames) {
        for (String cn : classNames) {
            try {
                Class.forName(cn, false, cl);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean matchesConditionalOnProperty(Environment env,
                                                       String prefix,
                                                       String name,
                                                       String havingValue,
                                                       boolean matchIfMissing) {
        String key = (prefix == null || prefix.isBlank()) ? name : (prefix + "." + name);
        String raw = env.getRaw(key);

        if (raw == null) {
            return matchIfMissing;
        }
        if (havingValue == null || havingValue.isBlank()) {
            return true;
        }
        return havingValue.equals(raw);
    }
}
