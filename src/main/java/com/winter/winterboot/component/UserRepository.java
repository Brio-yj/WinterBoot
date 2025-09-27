package com.winter.winterboot.component;

import com.winter.winterboot.annotation.Component;
import com.winter.winterboot.domain.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class UserRepository {

    private final Map<Integer, User> store = new java.util.concurrent.ConcurrentHashMap<>();
    public void save(User user) {
        store.put(user.getId(), user);
    }
    public User findById(int id) {
        return store.get(id);
    }

    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }
    public List<User> findByName(String name) {
        return store.values().stream()
                .filter(u -> u.getName().contains(name))
                .toList();
    }
}
