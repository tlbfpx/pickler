package com.heypickler.dto.app;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String nickname;
    private String city;
    private String avatarUrl;
}
