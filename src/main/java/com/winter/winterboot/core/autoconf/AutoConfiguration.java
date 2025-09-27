package com.winter.winterboot.core.autoconf;

import com.winter.winterboot.core.ApplicationContext;
import com.winter.winterboot.core.env.Environment;

public interface AutoConfiguration {
    void apply(ApplicationContext ctx, Environment env);
}
