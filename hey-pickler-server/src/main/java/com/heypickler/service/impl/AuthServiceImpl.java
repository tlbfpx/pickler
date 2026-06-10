package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.AesUtil;
import com.heypickler.common.util.JwtUtil;
import com.heypickler.common.util.WxBizDataCrypt;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.User;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final AdminUserMapper adminUserMapper;
    private final JwtUtil jwtUtil;
    private final AesUtil aesUtil;
    private final WxBizDataCrypt wxBizDataCrypt;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    @Value("${hey-pickler.wechat.appid}")
    private String appId;

    @Value("${hey-pickler.wechat.secret}")
    private String appSecret;

    @Value("${hey-pickler.wechat.dev-mode:false}")
    private boolean devMode;

    @Override
    public Map<String, Object> appLogin(String code) {
        String openid;
        String sessionKey;

        if (devMode) {
            // Dev mode: use code as openid directly, skip WeChat API call
            openid = "dev_" + code;
            sessionKey = "dev_session_" + code;
        } else {
            openid = callWxJsCode2Session(code);
            sessionKey = (String) redisTemplate.opsForValue().get(RedisKey.wxSession(openid));
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        boolean needBindPhone;
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setStatus("NORMAL");
            user.setStarPoints(0);
            user.setPartyPoints(0);
            user.setStarTier("SHINING");
            user.setPartyTier("SHINING");
            userMapper.insert(user);
            needBindPhone = true;
        } else {
            needBindPhone = user.getPhone() == null;
            user.setLastLoginAt(java.time.LocalDateTime.now());
            userMapper.updateById(user);
        }

        if ("BANNED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_BANNED);
        }

        redisTemplate.opsForValue().set(RedisKey.wxSession(openid), sessionKey, 30, TimeUnit.MINUTES);

        String token = jwtUtil.generateAppToken(user.getId());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("needBindPhone", needBindPhone);
        result.put("user", buildUserInfo(user));
        return result;
    }

    private Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> info = new java.util.HashMap<>();
        info.put("id", user.getId());
        info.put("nickname", user.getNickname());
        info.put("avatar_url", user.getAvatarUrl());
        info.put("phone", user.getPhone());
        info.put("city", user.getCity());
        info.put("star_points", user.getStarPoints());
        info.put("party_points", user.getPartyPoints());
        info.put("star_tier", user.getStarTier());
        info.put("party_tier", user.getPartyTier());
        info.put("status", user.getStatus());
        return info;
    }

    private String callWxJsCode2Session(String code) {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                URLEncoder.encode(appId, StandardCharsets.UTF_8),
                URLEncoder.encode(appSecret, StandardCharsets.UTF_8),
                URLEncoder.encode(code, StandardCharsets.UTF_8));

        String body = restTemplate.getForObject(url, String.class);
        Map<String, Object> result;
        try {
            result = new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信登录失败: 响应解析错误");
        }

        if (result == null || result.containsKey("errcode")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "微信登录失败");
        }

        return (String) result.get("openid");
    }

    @Override
    public void bindPhone(Long userId, String encryptedData, String iv) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        String sessionKey = (String) redisTemplate.opsForValue().get(RedisKey.wxSession(user.getOpenid()));
        if (sessionKey == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会话已过期，请重新登录");
        }

        // Decrypt phone number using WeChat's official AES/CBC scheme
        String phone = wxBizDataCrypt.decryptPhoneNumber(sessionKey, encryptedData, iv);
        user.setPhone(aesUtil.encrypt(phone));
        userMapper.updateById(user);
    }

    @Override
    public String refreshToken(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return jwtUtil.generateAppToken(userId);
    }

    @Override
    public Map<String, Object> adminLogin(String username, String password) {
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));

        if (admin == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }

        if ("DISABLED".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "账号已被禁用");
        }

        String token = jwtUtil.generateAdminToken(admin.getId(), admin.getRole());

        String sessionKey = RedisKey.adminSession(String.valueOf(admin.getId()));
        redisTemplate.opsForValue().set(sessionKey, token, 24, TimeUnit.HOURS);

        return Map.of("token", token, "role", admin.getRole());
    }
}
