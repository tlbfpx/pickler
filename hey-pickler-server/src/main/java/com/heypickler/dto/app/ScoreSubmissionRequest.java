package com.heypickler.dto.app;

import com.heypickler.vo.MatchVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ScoreSubmissionRequest {

    @NotEmpty(message = "比分不能为空")
    @Valid
    private List<MatchVO.GameScore> games;
}