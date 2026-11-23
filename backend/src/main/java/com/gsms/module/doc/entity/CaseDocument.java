package com.gsms.module.doc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_document")
public class CaseDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String docName;
    /** EVIDENCE / TEMPLATE_DOC / OTHER */
    private String docType;
    /** 相对路径，如 /files/2026/11/xxx.pdf */
    private String filePath;
    private Long fileSize;
    private String fileHash;
    private Integer version;
    private Long parentId;
    private String uploadBy;
    private LocalDateTime uploadTime;
    private Integer sensitiveFlag;
    @TableLogic
    private Integer deleted;
}
