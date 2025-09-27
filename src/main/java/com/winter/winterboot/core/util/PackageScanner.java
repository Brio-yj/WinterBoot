package com.winter.winterboot.core.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.winter.winterboot.annotation.Component;

public class PackageScanner {

    public Set<Class<?>> scanComponents(String basePackage) {
        Set<Class<?>> componentClasses = new HashSet<>();

        try {

            String packagePath = basePackage.replace('.', '/');
            var cl = Thread.currentThread().getContextClassLoader();
            var url = cl.getResource(packagePath);
            if (url == null) {
                throw new RuntimeException("패키지 리소스를 찾을 수 없습니다: " + basePackage + " (" + packagePath + ")");
            }

            URI uri = url.toURI();
            Path rootPath = Paths.get(uri);

            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .forEach(path -> {
                            try {
                                String relativePath = rootPath.relativize(path).toString();
                                String dotted = relativePath
                                        .replace('\\', '.')
                                        .replace('/', '.');

                                if (dotted.endsWith(".class")) {
                                    dotted = dotted.substring(0, dotted.length() - ".class".length());
                                }
                                // 내부 클래스 제거 (예: MyClass$1)
                                int dollarIdx = dotted.indexOf('$');
                                if (dollarIdx != -1) {
                                    dotted = dotted.substring(0, dollarIdx);
                                }

                                String fullClassName = basePackage + "." + dotted;
                                Class<?> loadedClass = Class.forName(fullClassName);

                                // (A) 인스턴스화 불가능한 것들 배제
                                int mod = loadedClass.getModifiers();
                                if (loadedClass.isAnnotation()
                                        || loadedClass.isInterface()
                                        || java.lang.reflect.Modifier.isAbstract(mod)
                                        || loadedClass.isEnum()
                                        || loadedClass.isRecord()) {
                                    return;
                                }

                                // (B) 빈 후보 판정
                                boolean directComponent = loadedClass.isAnnotationPresent(Component.class);
                                boolean metaComponent =
                                        java.util.Arrays.stream(loadedClass.getAnnotations())
                                                .map(a -> a.annotationType())
                                                .anyMatch(annoType -> annoType.isAnnotationPresent(Component.class));

                                if (directComponent || metaComponent) {
                                    componentClasses.add(loadedClass);
                                }
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException("클래스 로드 실패: " + path, e);
                            }
                        });
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("패키지 스캔 실패.", e);
        }
        return componentClasses;
    }
}
