package com.heypickler.vo;
import lombok.Data;
@Data
public class SysDictItemVO {
    private String itemKey;
    private String itemLabel;
    private String itemColor;
    private Integer sort;
    private String status;
    private String extraJson;
}
