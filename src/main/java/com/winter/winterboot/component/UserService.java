package com.winter.winterboot.component;

import com.winter.winterboot.annotation.Component;
import com.winter.winterboot.annotation.Inject;

@Component
public class UserService {

    @Inject
    private UserRepository userRepository;

}