package com.heypickler.service;

import com.heypickler.common.exception.BizException;

public interface ImageUrlValidator {
    void validate(String url) throws BizException;
}
