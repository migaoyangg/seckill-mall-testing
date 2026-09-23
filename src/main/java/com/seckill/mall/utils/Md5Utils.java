package com.seckill.mall.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Md5Utils {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private Md5Utils() {}

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
