package com.gsms.common;

import lombok.Getter;

/**
 * 统一错误码。业务码沿用页面清单第 4 节约定：
 * 40101 未认证 / 40301 无权限 / 40901 状态冲突 / 42301 归档只读
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "成功"),

    PARAM_INVALID(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    FORBIDDEN(40301, "无权限执行该操作"),
    NOT_FOUND(40401, "资源不存在"),
    METHOD_NOT_ALLOWED(40501, "请求方法不支持"),

    STATUS_CONFLICT(40901, "状态冲突或非法流转"),
    DUPLICATE(40902, "数据已存在"),
    ALREADY_ARCHIVED(42301, "归档案件只读"),
    CASE_CLOSED(42302, "案件已结案，不可再修改"),

    FILE_TYPE_NOT_ALLOWED(41501, "文件类型不允许"),
    FILE_TOO_LARGE(41301, "文件超出大小限制"),
    STORAGE_ERROR(50011, "文件存储失败"),

    SYSTEM_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
