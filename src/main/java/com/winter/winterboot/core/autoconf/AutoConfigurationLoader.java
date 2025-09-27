package com.winter.winterboot.core.autoconf;

import com.winter.winterboot.core.ApplicationContext;
import com.winter.winterboot.core.env.Environment;
import com.winter.winterboot.core.condition.ConditionalOnClass;
import com.winter.winterboot.core.condition.ConditionalOnProperty;
import com.winter.winterboot.core.condition.ConditionEvaluator;

import java.util.ServiceLoader;

public class AutoConfigurationLoader {

    public static void load(ApplicationContext ctx, Environment env) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        ServiceLoader<AutoConfiguration> loader = ServiceLoader.load(AutoConfiguration.class, cl);

        for (AutoConfiguration ac : loader) {
            Class<?> clazz = ac.getClass();

            // 1) @ConditionalOnClass
            ConditionalOnClass coc = clazz.getAnnotation(ConditionalOnClass.class);
            if (coc != null && !ConditionEvaluator.matchesConditionalOnClass(cl, coc.value())) {
                System.out.println("[AutoConfig] Skip " + clazz.getSimpleName() + " (missing class)");
                continue;
            }

            // 2) @ConditionalOnProperty
            ConditionalOnProperty cop = clazz.getAnnotation(ConditionalOnProperty.class);
            if (cop != null && !ConditionEvaluator.matchesConditionalOnProperty(
                    env, cop.prefix(), cop.name(), cop.havingValue(), cop.matchIfMissing())) {
                System.out.println("[AutoConfig] Skip " + clazz.getSimpleName() + " (property not matched)");
                continue;
            }
            ac.apply(ctx, env);
        }
    }
}
