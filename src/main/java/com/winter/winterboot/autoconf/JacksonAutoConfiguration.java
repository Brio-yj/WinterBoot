
package com.winter.winterboot.autoconf;

import com.winter.winterboot.core.autoconf.AutoConfiguration;
import com.winter.winterboot.core.ApplicationContext;
import com.winter.winterboot.core.env.Environment;
import com.winter.winterboot.core.condition.ConditionalOnClass;

@ConditionalOnClass({"com.fasterxml.jackson.databind.ObjectMapper"})
public class JacksonAutoConfiguration implements AutoConfiguration {
    @Override
    @SuppressWarnings("unchecked")
    public void apply(ApplicationContext ctx, Environment env) {
        try {
            Class<?> om = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            if (!ctx.containsBeanOfType(om)) {
                Object instance = om.getDeclaredConstructor().newInstance();
                ctx.registerBean((Class<Object>) om, instance);
                System.out.println("[AutoConfig] ObjectMapper registered");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to auto-configure ObjectMapper", e);
        }
    }
}
