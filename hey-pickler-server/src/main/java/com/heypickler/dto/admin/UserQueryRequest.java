package com.heypickler.dto.admin;

import lombok.Data;

@Data
public class UserQueryRequest {
    private String keyword;
    private String city;
    private String status;
    private String starTier;
    private int page;
    private int size;
}
