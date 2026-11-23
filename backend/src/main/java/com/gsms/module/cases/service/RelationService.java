package com.gsms.module.cases.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.common.PageResult;
import com.gsms.common.UserContext;
import com.gsms.module.cases.dto.RelationCreateRequest;
import com.gsms.module.cases.dto.RelationPageVO;
import com.gsms.module.cases.dto.RelationVO;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.entity.CaseRelation;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.mapper.CaseRelationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 争议关联：把工伤与劳动仲裁等案件按「案件对」聚合管理。
 *
 * 存储仍是 case_relation 的单向记录，展示时按案件对去重（主案取先建档的一方），
 * 这样旧数据无论从哪一侧建立过关联，都能在列表里合并成一行。
 */
@Service
public class RelationService {

    private final CaseRelationMapper relationMapper;
    private final CaseMainMapper caseMainMapper;
    private final CaseService caseService;

    public RelationService(CaseRelationMapper relationMapper, CaseMainMapper caseMainMapper,
                           CaseService caseService) {
        this.relationMapper = relationMapper;
        this.caseMainMapper = caseMainMapper;
        this.caseService = caseService;
    }

    public RelationPageVO page(RelationPageVO.Query query) {
        List<RelationVO> all = visiblePairs();

        RelationPageVO vo = new RelationPageVO();
        vo.setRelationCount(all.size());
        vo.setActiveCount((int) all.stream().filter(RelationVO::isActive).count());
        vo.setEmployeeCount(distinctEmployees(all));

        List<RelationVO> filtered = all.stream()
                .filter(row -> matchType(row, query.getCaseType()))
                .filter(row -> matchKeyword(row, query.getKeyword()))
                .collect(Collectors.toList());

        int size = Math.max(1, Math.min(query.getPageSize() == null ? 20 : query.getPageSize(), 100));
        int num = Math.max(1, query.getPageNum() == null ? 1 : query.getPageNum());
        int from = Math.min((num - 1) * size, filtered.size());

        PageResult<RelationVO> page = new PageResult<>();
        page.setTotal(filtered.size());
        page.setPageNum(num);
        page.setPageSize(size);
        page.setList(new ArrayList<>(filtered.subList(from, Math.min(from + size, filtered.size()))));
        vo.setPage(page);
        return vo;
    }

    /** 建立关联：主案/关联案都必须是系统内可访问的案件，重复关联直接拒绝 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(RelationCreateRequest req) {
        CaseMain main = byCaseNo(req.getMainCaseNo());
        CaseMain related = byCaseNo(req.getRelatedCaseNo());
        caseService.requireAccessible(main.getId());
        return link(main, related, req.getRelationDesc());
    }

    /**
     * 写入一条关联（内部复用）：统一按先建档的一方为主案，避免同一对出现两条反向记录。
     * 供新建案件时「带关联」直接调用，不再要求调用方先建后关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long link(CaseMain one, CaseMain other, String desc) {
        if (one.getId().equals(other.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能与自身建立关联");
        }
        CaseMain main = one.getId() < other.getId() ? one : other;
        CaseMain sub = main == one ? other : one;
        Long exists = relationMapper.selectCount(new LambdaQueryWrapper<CaseRelation>()
                .and(w -> w.and(x -> x.eq(CaseRelation::getCaseId, main.getId())
                                .eq(CaseRelation::getRelatedCaseId, sub.getId()))
                        .or(x -> x.eq(CaseRelation::getCaseId, sub.getId())
                                .eq(CaseRelation::getRelatedCaseId, main.getId()))));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "该关联关系已存在");
        }
        CaseRelation relation = new CaseRelation();
        relation.setCaseId(main.getId());
        relation.setRelatedCaseId(sub.getId());
        relation.setRelationType("RELATED");
        relation.setRelationDesc(desc);
        relation.setCreateBy(UserContext.userId());
        relation.setCreateTime(LocalDateTime.now());
        relation.setDeleted(0);
        relationMapper.insert(relation);
        return relation.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long relationId) {
        CaseRelation relation = relationMapper.selectById(relationId);
        if (relation == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "关联关系不存在");
        }
        caseService.requireAccessible(relation.getCaseId());
        relationMapper.deleteById(relationId);
    }

    /** 导出用明细（不分页，最多 5000 行） */
    public List<RelationVO> exportRows(RelationPageVO.Query query) {
        List<RelationVO> rows = visiblePairs().stream()
                .filter(row -> matchType(row, query.getCaseType()))
                .filter(row -> matchKeyword(row, query.getKeyword()))
                .limit(5000)
                .collect(Collectors.toList());
        return rows;
    }

    /**
     * 全部关联对：按案件对去重，主案 = 先建档的一方；已删除或无权查看的案件对过滤掉。
     */
    private List<RelationVO> visiblePairs() {
        List<CaseRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<CaseRelation>()
                .orderByAsc(CaseRelation::getId));
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> caseIds = new TreeSet<>();
        for (CaseRelation relation : relations) {
            caseIds.add(relation.getCaseId());
            caseIds.add(relation.getRelatedCaseId());
        }
        Map<Long, CaseMain> cases = caseMainMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(CaseMain::getId, c -> c, (a, b) -> a));

        Map<String, RelationVO> pairs = new LinkedHashMap<>();
        for (CaseRelation relation : relations) {
            CaseMain one = cases.get(relation.getCaseId());
            CaseMain other = cases.get(relation.getRelatedCaseId());
            if (one == null || other == null) {
                continue;
            }
            CaseMain main = one.getId() < other.getId() ? one : other;
            CaseMain sub = main == one ? other : one;
            String key = main.getId() + "-" + sub.getId();
            if (pairs.containsKey(key)) {
                continue;
            }
            if (!caseService.canViewCase(main) && !caseService.canViewCase(sub)) {
                continue;
            }
            pairs.put(key, toVO(relation, main, sub));
        }

        List<RelationVO> result = new ArrayList<>(pairs.values());
        result.sort(Comparator.comparing(RelationVO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private RelationVO toVO(CaseRelation relation, CaseMain main, CaseMain sub) {
        RelationVO vo = new RelationVO();
        vo.setId(relation.getId());
        vo.setMainCaseId(main.getId());
        vo.setMainCaseNo(main.getCaseNo());
        vo.setMainEmployee(main.getEmployeeName());
        vo.setMainCaseType(main.getCaseType());
        vo.setMainCaseTypeName(CaseDict.typeName(main.getCaseType()));
        vo.setMainStatus(main.getStatus());
        vo.setMainStatusName(CaseDict.statusName(main.getStatus()));
        vo.setMainOccurDate(main.getOccurDate());

        vo.setSubCaseId(sub.getId());
        vo.setSubCaseNo(sub.getCaseNo());
        vo.setSubEmployee(sub.getEmployeeName());
        vo.setSubCaseType(sub.getCaseType());
        vo.setSubCaseTypeName(CaseDict.typeName(sub.getCaseType()));
        vo.setSubStatus(sub.getStatus());
        vo.setSubStatusName(CaseDict.statusName(sub.getStatus()));
        vo.setSubOccurDate(sub.getOccurDate());

        vo.setRelationDesc(relation.getRelationDesc());
        vo.setCreateTime(relation.getCreateTime());
        vo.setActive(!isFinished(main.getStatus()) || !isFinished(sub.getStatus()));
        return vo;
    }

    private boolean isFinished(String status) {
        return CaseStateMachine.CLOSED.equals(status) || CaseStateMachine.ARCHIVED.equals(status);
    }

    private int distinctEmployees(List<RelationVO> rows) {
        Set<String> names = new TreeSet<>();
        for (RelationVO row : rows) {
            if (StringUtils.hasText(row.getMainEmployee())) {
                names.add(row.getMainEmployee().trim());
            }
            if (StringUtils.hasText(row.getSubEmployee())) {
                names.add(row.getSubEmployee().trim());
            }
        }
        return names.size();
    }

    private boolean matchType(RelationVO row, String caseType) {
        if (!StringUtils.hasText(caseType)) {
            return true;
        }
        return caseType.equals(row.getMainCaseType()) || caseType.equals(row.getSubCaseType());
    }

    private boolean matchKeyword(RelationVO row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String kw = keyword.trim();
        return contains(row.getMainCaseNo(), kw) || contains(row.getSubCaseNo(), kw)
                || contains(row.getMainEmployee(), kw) || contains(row.getSubEmployee(), kw);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private CaseMain byCaseNo(String caseNo) {
        CaseMain entity = caseMainMapper.selectOne(new LambdaQueryWrapper<CaseMain>()
                .eq(CaseMain::getCaseNo, Objects.requireNonNull(caseNo).trim()));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "案件不存在: " + caseNo);
        }
        return entity;
    }
}
