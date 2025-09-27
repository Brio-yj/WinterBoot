// com.winter.winterboot.autoconf.WebServerAutoConfiguration
package com.winter.winterboot.autoconf;

import com.winter.winterboot.DispatcherHandler;
import com.winter.winterboot.core.autoconf.AutoConfiguration;
import com.winter.winterboot.core.ApplicationContext;
import com.winter.winterboot.core.env.Environment;
import com.winter.winterboot.core.condition.ConditionalOnProperty;

import java.io.IOException;
import java.net.InetSocketAddress;

@ConditionalOnProperty(prefix = "server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebServerAutoConfiguration implements AutoConfiguration {
    @Override
    public void apply(ApplicationContext ctx, Environment env) {
        // DispatcherHandler
        if (!ctx.containsBeanOfType(DispatcherHandler.class)) {
            DispatcherHandler handler = new DispatcherHandler(ctx);
            ctx.registerBean(DispatcherHandler.class, handler);
            System.out.println("[AutoConfig] DispatcherHandler registered");
        }

        // HttpServer
        if (!ctx.containsBeanOfType(com.sun.net.httpserver.HttpServer.class)) {
            int port = env.getInt("server.port", 8080);
            try {
                var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
                DispatcherHandler handler = ctx.getBean(DispatcherHandler.class);
                server.createContext("/", handler);
                server.setExecutor(null);
                ctx.registerBean(com.sun.net.httpserver.HttpServer.class, server);
                server.start();
                System.out.println("[AutoConfig] HttpServer started at port " + port);
            } catch (IOException e) {
                throw new RuntimeException("Failed to start HttpServer", e);
            }
        }
    }
}
