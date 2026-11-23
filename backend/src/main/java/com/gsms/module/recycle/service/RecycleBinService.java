package com.gsms.module.recycle.service;

import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.common.LoginUser;
import com.gsms.common.PageResult;
import com.gsms.common.UserContext;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.doc.service.StorageService;
import com.gsms.module.recycle.dto.RecycleBatchRequest;
import com.gsms.module.recycle.dto.RecycleItemVO;
import com.gsms.module.recycle.dto.RecycleQuery;
import com.gsms.module.recycle.mapper.RecycleBinMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 回收站：查询已软删除的案件/用户，支持恢复与永久删除（物理删除，含关联数据）。
 * 默认只有系统管理员可查看用户回收站；案件回收站对案件管理员开放。
 */
@Service
public class RecycleBinService {

    private static final String ROLE_SYS_ADMIN = "ROLE_SYS_ADMIN";
    private static final String ROLE_CASE_ADMIN = "ROLE_CASE_ADMIN";
    private static final int MAX_PAGE_SIZE = 100;

    private final RecycleBinMapper mapper;
    private final AuditService auditService;
    private final StorageService storageService;

    public RecycleBinService(RecycleBinMapper mapper, AuditService auditService,
                             StorageService storageService) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.storageService = storageService;
    }

    public PageResult<RecycleItemVO> page(RecycleQuery query) {
        String target = normalizeTarget(query.getTarget());
        assertView(target);
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (pageNum - 1) * pageSize;

        PageResult<RecycleItemVO> result = new PageResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        if ("USER".equals(target)) {
            result.setTotal(mapper.countDeletedUsers(keyword));
            result.setList(mapper.pageDeletedUsers(keyword, offset, pageSize));
        } else {
            result.setTotal(mapper.countDeletedCases(keyword));
            result.setList(mapper.pageDeletedCases(keyword, offset, pageSize));
        }
        return result;
    }

    /** 恢复：把 deleted 置回 0，数据原样回到业务列表 */
    @Transactional(rollbackFor = Exception.class)
    public int restore(RecycleBatchRequest request) {
        String target = normalizeTarget(request.getTarget());
        assertManage(target);
        List<Long> ids = normalizeIds(request.getIds());
        int count = "USER".equals(target) ? mapper.restoreUsers(ids) : mapper.restoreCases(ids);
        auditService.success("回收站", "恢复" + label(target), String.join(",", toStrings(ids)), "共 " + count + " 条");
        return count;
    }

    /** 永久删除：物理删除记录及其全部关联数据，不可恢复 */
    @Transactional(rollbackFor = Exception.class)
    public int purge(RecycleBatchRequest request) {
        String target = normalizeTarget(request.getTarget());
        assertManage(target);
        List<Long> ids = normalizeIds(request.getIds());

        if ("USER".equals(target)) {
            List<String> userIds = mapper.selectUserIds(ids);
            assertAllInRecycleBin(userIds.size(), ids.size(), "用户");
            mapper.purgeUserRoleByUserIds(ids);
            int count = mapper.purgeUsers(ids);
            auditService.success("回收站", "永久删除用户", String.join(",", userIds), "共 " + count + " 个账号及其角色关系");
            return count;
        }

        List<String> caseNos = mapper.selectCaseNos(ids);
        // 关联表按 case_id 整批删除且不带 deleted 条件，因此必须先确认全部是回收站内的案件，
        // 否则传入在办案件 id 会先删掉它的文档/成员/日志，再删 0 行，造成数据残缺。
        assertAllInRecycleBin(caseNos.size(), ids.size(), "案件");
        // 先取文件路径，再删记录：文件不清会留下无法追溯的孤儿附件
        List<String> filePaths = mapper.selectDocumentPaths(ids);
        mapper.purgeCaseInjuryExt(ids);
        mapper.purgeCaseArbitrationExt(ids);
        mapper.purgeCaseMember(ids);
        mapper.purgeCaseStatusLog(ids);
        mapper.purgeCaseRelation(ids);
        mapper.purgeCaseDocument(ids);
        mapper.purgeDeadlineReminder(ids);
        mapper.purgeWfInstance(ids);
        mapper.purgeTask(ids);
        mapper.purgeCountersign(ids);
        mapper.purgeNotification(ids);
        int count = mapper.purgeCases(ids);
        for (String path : filePaths) {
            if (path != null && !path.isEmpty()) {
                storageService.deleteQuietly(path);
            }
        }
        auditService.success("回收站", "永久删除案件", String.join(",", caseNos),
                "共 " + count + " 件，关联的扩展信息、成员、日志、文书、期限、流程与协同数据一并删除");
        return count;
    }

    private void assertView(String target) {
        LoginUser current = UserContext.get();
        if ("USER".equals(target) && !current.hasRole(ROLE_SYS_ADMIN)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅系统管理员可查看用户回收站");
        }
        if (!current.hasRole(ROLE_SYS_ADMIN) && !current.hasRole(ROLE_CASE_ADMIN)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅系统管理员或案件管理员可访问回收站");
        }
    }

    private void assertManage(String target) {
        assertView(target);
    }

    /** 入参去重并拒绝空集合：空 ids 会让 IN () 变成 SQL 语法错误 */
    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择需要操作的记录");
        }
        List<Long> distinct = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(ids));
        distinct.removeIf(java.util.Objects::isNull);
        if (distinct.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择需要操作的记录");
        }
        return distinct;
    }

    /** 永久删除只允许回收站内（deleted=1）的记录，防止传入在办数据 id 绕过软删除 */
    private void assertAllInRecycleBin(int found, int expected, String label) {
        if (found != expected) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "只能永久删除回收站中的" + label + "，请刷新列表后重试");
        }
    }

    private String normalizeTarget(String target) {
        return "USER".equalsIgnoreCase(target) ? "USER" : "CASE";
    }

    private String label(String target) {
        return "USER".equals(target) ? "用户" : "案件";
    }

    private List<String> toStrings(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
    }
}
