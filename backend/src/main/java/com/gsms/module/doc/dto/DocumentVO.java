package com.gsms.module.doc.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {

    private Long id;
    private Long caseId;
    private String docName;
    private String docType;
    private String docTypeName;
    private Long fileSize;
    private Integer version;
    private Long parentId;
    private String uploadBy;
    private String uploadByName;
    private LocalDateTime uploadTime;
    private Integer sensitiveFlag;
    /** 当前用户是否可下载（敏感文档对非授权角色关闭） */
    private boolean canDownload;
    private String fileHash;
}
