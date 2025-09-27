package com.winter.winterboot;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.winter.winterboot.DispatcherHandler;
import com.winter.winterboot.core.ApplicationContext;
import com.winter.winterboot.core.autoconf.AutoConfigurationLoader;
import com.winter.winterboot.core.env.Environment;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;


public class WinterBootApplication {
    public static void run(Class<?> mainClass) {
        ApplicationContext context = new ApplicationContext("com.winter.winterboot");
        Environment env = new Environment();
        AutoConfigurationLoader.load(context, env);
        System.out.println("WinterBoot application started.");
    }
}
