package com.winter.winterboot.controller;

import com.winter.winterboot.annotation.*;
import com.winter.winterboot.component.UserRepository;
import com.winter.winterboot.domain.User;

import java.util.List;

@RestController
public class UserController {

    @Inject
    private UserRepository userRepository;

    @GetMapping("/")
    public String home() {
        return "WinterBoot";
    }

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable("id") int id) {
        User found = userRepository.findById(id);
        return (found != null) ? found : new User(id, "unknown");
    }

    @GetMapping("/users")
    public List<User> list(
            @RequestParam("name") String name,
            @RequestParam(value="limit", required=false, defaultValue="10") int limit
    ) {

        return userRepository.findAll().stream()
                .filter(u -> u.getName().contains(name))
                .limit(limit)
                .toList();
    }

    @PostMapping("/users")
    public String createUser(@RequestBody User newUser) {
        userRepository.save(newUser);
        return "ok";
    }
}


