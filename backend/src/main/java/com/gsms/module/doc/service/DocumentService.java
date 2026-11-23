package com.gsms.module.doc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.*;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.service.CaseService;
import com.gsms.module.doc.dto.DocumentVO;
import com.gsms.module.doc.entity.CaseDocument;
import com.gsms.module.doc.mapper.CaseDocumentMapper;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysUserMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentService {

    private static final Map<String, String> DOC_TYPE_NAME = new HashMap<>();
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

    static {
        DOC_TYPE_NAME.put("EVIDENCE", "证据材料");
        DOC_TYPE_NAME.put("TEMPLATE_DOC", "生成文书");
        DOC_TYPE_NAME.put("OTHER", "其他");

        CONTENT_TYPES.put("pdf", "application/pdf");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("jpeg", "image/jpeg");
        CONTENT_TYPES.put("doc", "application/msword");
        CONTENT_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        CONTENT_TYPES.put("xls", "application/vnd.ms-excel");
        CONTENT_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        CONTENT_TYPES.put("zip", "application/zip");
        CONTENT_TYPES.put("rar", "application/vnd.rar");
    }

    private final CaseDocumentMapper documentMapper;
    private final CaseService caseService;
    private final StorageService storageService;
    private final AuditService auditService;
    private final SysUserMapper userMapper;

    public DocumentService(CaseDocumentMapper documentMapper, CaseService caseService,
                           StorageService storageService,
                           AuditService auditService, SysUserMapper userMapper) {
        this.documentMapper = documentMapper;
        this.caseService = caseService;
        this.storageService = storageService;
        this.auditService = auditService;
        this.userMapper = userMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long upload(Long caseId, MultipartFile file, String docType, Integer sensitiveFlag) {
        CaseMain caseMain = caseService.requireAccessible(caseId);
        if (caseMain.getArchiveStatus() != null && caseMain.getArchiveStatus() == 1) {
            throw new BizException(ErrorCode.ALREADY_ARCHIVED);
        }
        StorageService.StoredFile stored = storageService.store(file, caseMain.getCaseNo());

        CaseDocument entity = new CaseDocument();
        entity.setCaseId(caseId);
        entity.setDocName(stored.originalName);
        entity.setDocType(StringUtils.hasText(docType) ? docType : "OTHER");
        entity.setFilePath(stored.relativePath);
        entity.setFileSize(stored.size);
        entity.setFileHash(stored.sha256);
        entity.setVersion(1);
        entity.setUploadBy(UserContext.userId());
        entity.setUploadTime(LocalDateTime.now());
        entity.setSensitiveFlag(sensitiveFlag == null ? 0 : sensitiveFlag);
        entity.setDeleted(0);
        documentMapper.insert(entity);

        auditService.success("文档中心", "上传文档", String.valueOf(entity.getId()),
                String.format("caseNo=%s, docName=%s, size=%d", caseMain.getCaseNo(), stored.originalName, stored.size));
        return entity.getId();
    }

    /** 上传新版本：沿用父文档名称，版本号 +1，parent_id 指向首版 */
    @Transactional(rollbackFor = Exception.class)
    public Long newVersion(Long documentId, MultipartFile file, String versionNote) {
        CaseDocument parent = requireDocument(documentId);
        CaseMain caseMain = caseService.requireAccessible(parent.getCaseId());
        if (caseMain.getArchiveStatus() != null && caseMain.getArchiveStatus() == 1) {
            throw new BizException(ErrorCode.ALREADY_ARCHIVED);
        }
        StorageService.StoredFile stored = storageService.store(file, caseMain.getCaseNo());
        Long rootId = parent.getParentId() == null ? parent.getId() : parent.getParentId();

        CaseDocument entity = new CaseDocument();
        entity.setCaseId(parent.getCaseId());
        entity.setDocName(parent.getDocName());
        entity.setDocType(parent.getDocType());
        entity.setFilePath(stored.relativePath);
        entity.setFileSize(stored.size);
        entity.setFileHash(stored.sha256);
        entity.setVersion(nextVersion(rootId) + 1);
        entity.setParentId(rootId);
        entity.setUploadBy(UserContext.userId());
        entity.setUploadTime(LocalDateTime.now());
        entity.setSensitiveFlag(parent.getSensitiveFlag());
        entity.setDeleted(0);
        documentMapper.insert(entity);

        auditService.success("文档中心", "上传新版本", String.valueOf(entity.getId()),
                String.format("parentId=%d, version=%d, note=%s", rootId, entity.getVersion(), versionNote));
        return entity.getId();
    }

    private int nextVersion(Long rootId) {
        List<CaseDocument> versions = documentMapper.selectList(new LambdaQueryWrapper<CaseDocument>()
                .eq(CaseDocument::getParentId, rootId));
        int max = 1;
        CaseDocument root = documentMapper.selectById(rootId);
        if (root != null) {
            max = Math.max(max, root.getVersion() == null ? 1 : root.getVersion());
        }
        for (CaseDocument item : versions) {
            max = Math.max(max, item.getVersion() == null ? 1 : item.getVersion());
        }
        return max;
    }

    public List<DocumentVO> list(Long caseId) {
        // 案件只取一次，供整页文档复用授权判断，避免逐条回查（N+1）
        CaseMain caseMain = caseService.requireAccessible(caseId);
        List<CaseDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<CaseDocument>()
                .eq(CaseDocument::getCaseId, caseId)
                .orderByDesc(CaseDocument::getUploadTime)
                .orderByDesc(CaseDocument::getId));
        if (documents.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> uploaders = documents.stream().map(CaseDocument::getUploadBy).collect(Collectors.toSet());
        Map<String, String> names = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUserId, uploaders)).stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName, (a, b) -> a));

        List<DocumentVO> result = new ArrayList<>(documents.size());
        for (CaseDocument doc : documents) {
            DocumentVO vo = new DocumentVO();
            vo.setId(doc.getId());
            vo.setCaseId(doc.getCaseId());
            vo.setDocName(doc.getDocName());
            vo.setDocType(doc.getDocType());
            vo.setDocTypeName(DOC_TYPE_NAME.getOrDefault(doc.getDocType(), doc.getDocType()));
            vo.setFileSize(doc.getFileSize());
            vo.setVersion(doc.getVersion());
            vo.setParentId(doc.getParentId());
            vo.setUploadBy(doc.getUploadBy());
            vo.setUploadByName(names.getOrDefault(doc.getUploadBy(), doc.getUploadBy()));
            vo.setUploadTime(doc.getUploadTime());
            vo.setSensitiveFlag(doc.getSensitiveFlag());
            vo.setFileHash(doc.getFileHash());
            vo.setCanDownload(canDownload(doc, caseMain));
            result.add(vo);
        }
        return result;
    }

    @Data
    public static class DownloadResult {
        /** 物理文件：由控制器流式输出，避免整文件读入堆内存 */
        private Path path;
        private String fileName;
        private String contentType;
    }

    public DownloadResult download(Long documentId, boolean inline) {
        CaseDocument entity = requireDocument(documentId);
        CaseMain caseMain = caseService.requireAccessible(entity.getCaseId());
        if (!canDownload(entity, caseMain)) {
            auditService.fail("文档中心", "下载文档", String.valueOf(documentId), "敏感文档需审批");
            throw new BizException(ErrorCode.FORBIDDEN, "该文档为敏感文档，下载需审批");
        }

        String path = entity.getFilePath();
        DownloadResult result = new DownloadResult();
        result.setFileName(entity.getDocName());
        result.setContentType(CONTENT_TYPES.getOrDefault(extensionOf(path), "application/octet-stream"));
        // resolve 内已完成路径合法性与存在性校验，此处只取路径，内容由控制器流式输出
        result.setPath(storageService.resolve(path));

        auditService.success("文档中心", inline ? "预览文档" : "下载文档", String.valueOf(documentId),
                String.format("caseNo=%s, docName=%s", caseMain.getCaseNo(), entity.getDocName()));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long documentId) {
        CaseDocument entity = requireDocument(documentId);
        CaseMain caseMain = caseService.requireAccessible(entity.getCaseId());
        if (caseMain.getArchiveStatus() != null && caseMain.getArchiveStatus() == 1) {
            throw new BizException(ErrorCode.ALREADY_ARCHIVED);
        }
        LoginUser current = UserContext.get();
        if (!current.getUserId().equals(entity.getUploadBy())
                && !current.hasRole("ROLE_SYS_ADMIN") && !current.hasRole("ROLE_CASE_ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅上传人或管理员可删除文档");
        }
        // 逻辑删除，物理文件保留以支持审计追溯
        documentMapper.deleteById(documentId);
        auditService.success("文档中心", "删除文档", String.valueOf(documentId), entity.getDocName());
    }

    public List<DocumentVO> versions(Long documentId) {
        CaseDocument entity = requireDocument(documentId);
        // 版本列表同样需要案件访问权，且只查一次案件供整页复用
        CaseMain caseMain = caseService.requireAccessible(entity.getCaseId());
        Long rootId = entity.getParentId() == null ? entity.getId() : entity.getParentId();
        List<CaseDocument> all = documentMapper.selectList(new LambdaQueryWrapper<CaseDocument>()
                .eq(CaseDocument::getCaseId, entity.getCaseId())
                .and(w -> w.eq(CaseDocument::getId, rootId).or().eq(CaseDocument::getParentId, rootId))
                .orderByAsc(CaseDocument::getVersion));
        List<DocumentVO> result = new ArrayList<>();
        for (CaseDocument doc : all) {
            DocumentVO vo = new DocumentVO();
            vo.setId(doc.getId());
            vo.setDocName(doc.getDocName());
            vo.setVersion(doc.getVersion());
            vo.setFileSize(doc.getFileSize());
            vo.setUploadBy(doc.getUploadBy());
            vo.setUploadTime(doc.getUploadTime());
            vo.setCanDownload(canDownload(doc, caseMain));
            result.add(vo);
        }
        return result;
    }

    public CaseDocument requireDocument(Long id) {
        CaseDocument entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return entity;
    }

    /**
     * 敏感文档下载授权：管理员、案件管理员、承办人/负责人可下载；
     * 协办人、会签人、阅知人需走下载审批（当前版本未建审批表，直接拒绝并提示）。
     * 案件由调用方统一加载后传入，避免逐条文档回查数据库。
     */
    private boolean canDownload(CaseDocument doc, CaseMain caseMain) {
        LoginUser current = UserContext.get();
        if (current.hasRole("ROLE_SYS_ADMIN") || current.hasRole("ROLE_CASE_ADMIN")) {
            return true;
        }
        boolean sensitive = doc.getSensitiveFlag() != null && doc.getSensitiveFlag() == 1;
        if (sensitive) {
            return current.getUserId().equals(caseMain.getOwnerId())
                    || current.getUserId().equals(caseMain.getHandlerId());
        }
        return true;
    }

    private String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
