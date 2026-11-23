package com.gsms.module.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 自定义字典项请求（当前用于厂区等业务可选值） */
@Data
public class DictItemRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称不能超过 100 字")
    private String name;
}
