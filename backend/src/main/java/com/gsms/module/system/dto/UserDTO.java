package com.gsms.module.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public class UserDTO {

    @Data
    public static class SaveRequest {
        @NotBlank(message = "登录名不能为空")
        @Size(max = 32, message = "登录名不能超过 32 字符")
        private String userId;

        @NotBlank(message = "姓名不能为空")
        private String userName;

        private Long deptId;
        private String mobile;
        private String email;
        private String position;
        private Integer status = 1;
        /** 新建时必填；修改时留空表示不改密码 */
        private String password;
        private List<Long> roleIds;
    }

    @Data
    public static class UserVO {
        private Long id;
        private String userId;
        private String userName;
        private Long deptId;
        private String deptName;
        private String mobile;
        private String email;
        private String position;
        private Integer status;
        private List<String> roleCodes;
        private List<String> roleNames;
        private LocalDateTime lastLoginTime;
        private LocalDateTime createTime;
    }

    @Data
    public static class Query {
        private Integer pageNum = 1;
        private Integer pageSize = 20;
        private String keyword;
        private Long deptId;
        private Integer status;
    }
}
