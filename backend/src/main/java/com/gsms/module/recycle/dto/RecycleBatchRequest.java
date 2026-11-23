package com.gsms.module.recycle.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.util.List;

/** 回收站批量操作请求：恢复 / 永久删除 */
@Data
public class RecycleBatchRequest {

    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "CASE|USER", message = "目标类型只能是 CASE/USER")
    private String target;

    @NotEmpty(message = "请至少选择一条记录")
    private List<Long> ids;
}
