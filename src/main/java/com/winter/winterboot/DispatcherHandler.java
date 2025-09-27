package com.winter.winterboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.winter.winterboot.annotation.*;
import com.winter.winterboot.core.ApplicationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherHandler implements HttpHandler {

    private final ApplicationContext context;

    private final Map<String, Map<String, MethodInfo>> handlerMapping = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DispatcherHandler(ApplicationContext context) {
        this.context = context;
        initHandlerMapping();
    }

    private void initHandlerMapping() {
        for (Object bean : context.getBeans().values()) {
            Class<?> beanClass = bean.getClass();
            boolean isController = beanClass.isAnnotationPresent(Controller.class);
            boolean isRestController = beanClass.isAnnotationPresent(RestController.class);

            if (isController || isRestController) {
                for (Method method : beanClass.getDeclaredMethods()) {
                    String path = null;
                    String httpMethod = null;

                    if (method.isAnnotationPresent(GetMapping.class)) {
                        path = method.getAnnotation(GetMapping.class).value();
                        httpMethod = "GET";
                    } else if (method.isAnnotationPresent(PostMapping.class)) {
                        path = method.getAnnotation(PostMapping.class).value();
                        httpMethod = "POST";
                    }
                    if (path != null && httpMethod != null) {
                        handlerMapping
                                .computeIfAbsent(path, k -> new HashMap<>())
                                .put(httpMethod, new MethodInfo(bean, method, isRestController, path)); // ← path 저장!
                        System.out.printf("Mapped [%s] %s to %s%n", httpMethod, path, method.getName());
                    }
                }
            }
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String httpMethod  = exchange.getRequestMethod();

        MethodInfo methodInfo = findMethodInfo(requestPath, httpMethod);
        if (methodInfo == null) {
            String notFound = "404 Not Found";
            exchange.sendResponseHeaders(404, notFound.length());
            try (OutputStream os = exchange.getResponseBody()) { os.write(notFound.getBytes()); }
            return;
        }

        try {
            //  리플렉션
            Object controller = methodInfo.getBean();
            Method handlerMethod = methodInfo.getMethod();
            Parameter[] parameters = handlerMethod.getParameters();
            Object[] args = new Object[parameters.length];

            // (A) PathVariable 필요 시 추출
            boolean needPathVars = Arrays.stream(parameters).anyMatch(p -> p.isAnnotationPresent(PathVariable.class));
            Map<String,String> pathVariables = needPathVars
                    ? extractPathVariables(methodInfo.getMappingPath(), requestPath)
                    : Collections.emptyMap();
            // (B) Query 파라미터 파싱 (한 번만)
            Map<String, List<String>> queryParams = parseQueryParams(exchange.getRequestURI());
            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];

                if (param.isAnnotationPresent(RequestBody.class)) {
                    args[i] = objectMapper.readValue(exchange.getRequestBody(), param.getType());
                    continue;
                }

                if (param.isAnnotationPresent(PathVariable.class)) {
                    String varName = param.getAnnotation(PathVariable.class).value();
                    String varValue = pathVariables.get(varName);
                    if (varValue == null) {
                        send400(exchange, "Missing path variable: " + varName);
                        return;
                    }
                    args[i] = convertSingle(varValue, param.getType());
                    continue;
                }

                if (param.isAnnotationPresent(RequestParam.class)) {
                    RequestParam rp = param.getAnnotation(RequestParam.class);
                    String name = rp.value();
                    List<String> values = queryParams.getOrDefault(name, Collections.emptyList());

                    if (values.isEmpty()) {
                        if (rp.required() && rp.defaultValue().isEmpty()) {
                            send400(exchange, "Missing required query parameter: " + name);
                            return;
                        }

                        if (!rp.defaultValue().isEmpty()) {
                            values = List.of(rp.defaultValue());
                        }
                    }
                    args[i] = convertMulti(values, param.getType(), param.getParameterizedType());
                    continue;
                }

                if (param.getType().equals(HttpExchange.class)) {
                    args[i] = exchange;
                    continue;
                }
            }

            Object result = handlerMethod.invoke(controller, args);

            if (methodInfo.isRestController()) {
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    objectMapper.writeValue(os, result);
                }
            }

            else {
                String response = (result != null) ? result.toString() : "";
                exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        catch (Exception e) {
            String error = "Internal Server Error";
            exchange.sendResponseHeaders(500, error.length());
            try (OutputStream os = exchange.getResponseBody()) { os.write(error.getBytes()); }
            e.printStackTrace();
        }
    }


    private void send400(HttpExchange exchange, String msg) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
        byte[] body = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(400, body.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
    }


    private MethodInfo findMethodInfo(String requestPath, String httpMethod) {
        for (Map.Entry<String, Map<String, MethodInfo>> entry : handlerMapping.entrySet()) {
            String mappingPath = entry.getKey();
            if (isPathMatch(mappingPath, requestPath)) {
                return entry.getValue().get(httpMethod);
            }
        }
        return null;
    }


    private boolean isPathMatch(String pattern, String path) {
        String[] patternSegments = pattern.split("/");
        String[] pathSegments = path.split("/");
        if (patternSegments.length != pathSegments.length) {
            return false;
        }
        for (int i = 0; i < patternSegments.length; i++) {
            String p = patternSegments[i];
            String c = pathSegments[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                continue; // 경로 변수이므로 일치하는 것으로 간주
            }
            if (!p.equals(c)) {
                return false;
            }
        }
        return true;
    }


    private Map<String, String> extractPathVariables(String pattern, String path) {
        Map<String, String> variables = new HashMap<>();
        String[] patternSegments = pattern.split("/");
        String[] pathSegments = path.split("/");
        for (int i = 0; i < patternSegments.length; i++) {
            String p = patternSegments[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                String varName = p.substring(1, p.length() - 1);
                variables.put(varName, pathSegments[i]);
            }
        }
        return variables;
    }


    private Map<String, List<String>> parseQueryParams(URI uri) {
        String query = uri.getRawQuery();
        Map<String, List<String>> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;

        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String rawKey = (idx > 0) ? pair.substring(0, idx) : pair;
            String rawVal = (idx > 0) ? pair.substring(idx + 1) : "";

            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            String val = URLDecoder.decode(rawVal, StandardCharsets.UTF_8);

            params.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }
        return params;
    }


    @SuppressWarnings("unchecked")
    private Object convertSingle(String value, Class<?> t) {
        if (t == String.class) return value;
        if (t == int.class || t == Integer.class) return Integer.parseInt(value);
        if (t == long.class || t == Long.class) return Long.parseLong(value);
        if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(value);
        if (t == double.class || t == Double.class) return Double.parseDouble(value);
        if (t.isEnum()) return Enum.valueOf((Class<Enum>) t, value);
        return value; // fallback
    }


    private Object convertMulti(List<String> values, Class<?> targetType, Type genericType) {
        // 배열
        if (targetType.isArray()) {
            Class<?> component = targetType.getComponentType();
            Object array = Array.newInstance(component, values.size());
            for (int i = 0; i < values.size(); i++) {
                Array.set(array, i, convertSingle(values.get(i), component));
            }
            return array;
        }
        // List<T>
        if (List.class.isAssignableFrom(targetType) && genericType instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            Class<?> elemType = (arg instanceof Class) ? (Class<?>) arg : String.class;
            List<Object> out = new ArrayList<>(values.size());
            for (String v : values) out.add(convertSingle(v, elemType));
            return out;
        }
        // 단일값
        String first = values.isEmpty() ? null : values.get(0);
        return convertSingle(first, targetType);
    }

    @Getter
    @AllArgsConstructor
    private static class MethodInfo {
        private final Object bean;
        private final Method method;
        private final boolean isRestController;
        private final String mappingPath;

        public MethodInfo(Object bean, Method method, boolean isRestController) {
            this(bean, method, isRestController, null);
        }
    }
}