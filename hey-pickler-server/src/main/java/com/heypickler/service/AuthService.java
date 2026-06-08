package com.heypickler.service;

import com.heypickler.entity.User;
import java.util.Map;

public interface AuthService {
    Map<String, Object> appLogin(String code);
    void bindPhone(Long userId, String encryptedData, String iv);
    String refreshToken(Long userId);
    Map<String, Object> adminLogin(String username, String password);
}
