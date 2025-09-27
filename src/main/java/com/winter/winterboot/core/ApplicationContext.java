package com.winter.winterboot.core;

import com.winter.winterboot.annotation.Inject;
import com.winter.winterboot.core.util.PackageScanner;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ApplicationContext {

    private final Map<Class<?>, Object> beans = new HashMap<>();

    public ApplicationContext(String basePackage) {
        Set<Class<?>> componentClasses = new PackageScanner().scanComponents(basePackage);

        componentClasses.forEach(this::createBean);

        beans.values().forEach(this::injectDependencies);

        System.out.println("application 생성자 동작 완료 ");
    }

    private void createBean(Class<?> componentClass) {
        System.out.println("componentClass \n" +componentClass);
        try {
            Object instance = componentClass.getDeclaredConstructor().newInstance();
            System.out.println("instance= " + instance);
            beans.put(componentClass, instance);
        } catch (Exception e) {
            throw new RuntimeException("빈 생성 실패: " + componentClass.getName(), e);
        }
    }

    private void injectDependencies(Object bean) {
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                Class<?> dependencyType = field.getType();
                Object dependency = resolveByAssignableType(dependencyType);
                if (dependency != null) {
                    try {
                        field.setAccessible(true);
                        field.set(bean, dependency);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("의존성 주입 실패: " + field.getName(), e);
                    }
                } else {
                    throw new RuntimeException("주입 대상 빈을 찾지 못함: " + dependencyType.getName());
                }
                System.out.printf("Inject %s into %s.%s%n",
                        dependencyType.getSimpleName(),
                        bean.getClass().getSimpleName(),
                        field.getName());
            }

        }
    }

    private Object resolveByAssignableType(Class<?> dependencyType) {
        Object exact = beans.get(dependencyType);
        if (exact != null) return exact;

        Object candidate = null;
        for (Map.Entry<Class<?>, Object> e : beans.entrySet()) {
            if (dependencyType.isAssignableFrom(e.getKey())) {
                if (candidate != null) {
                    throw new RuntimeException("주입 후보가 둘 이상입니다: " + dependencyType.getName());
                }
                candidate = e.getValue();
            }
        }
        return candidate;

    }

    public Map<Class<?>, Object> getBeans() { return beans; }

    public <T> T getBean(Class<T> type) {
        Object o = resolveByAssignableType(type);
        return type.cast(o);
    }

    public synchronized <T> void registerBean(Class<T> type, T instance) {
        // 사용자가 정의한 빈을 덮지 않음
        if (!containsBeanOfType(type)) {
            beans.put(type, instance);
        }
    }

    public boolean containsBeanOfType(Class<?> type) {
        if (beans.containsKey(type)) return true;
        for (Class<?> key : beans.keySet()) {
            if (type.isAssignableFrom(key)) return true;
        }
        return false;
    }
}
