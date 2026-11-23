package com.gsms.module.cases.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.SimpleColumnWidthStyleStrategy;
import com.alibaba.excel.write.style.row.SimpleRowHeightStyleStrategy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.module.cases.dto.CaseImportRow;
import com.gsms.module.cases.entity.CaseMain;
import com.gsms.module.cases.entity.WfNodeDef;
import com.gsms.module.cases.mapper.CaseMainMapper;
import com.gsms.module.cases.support.CaseImportColumns;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史案件批量导入。
 *
 * 流程：下载模板 → 上传预检（只校验不写库，返回每行错误）→ 确认导入（再校验一次后整批事务写入）。
 * 预检与导入共用同一套解析/校验逻辑，避免「预检通过、导入失败」。
 */
@Slf4j
@Service
public class CaseImportService {

    /** 单次导入上限，防止一次几万行拖垮事务 */
    private static final int MAX_ROWS = 1000;

    private static final Map<String, String> TYPE_BY_TEXT = new LinkedHashMap<>();
    private static final Map<String, String> RECOGNIZE_BY_TEXT = new LinkedHashMap<>();
    private static final Map<String, String> RESULT_BY_TEXT = new LinkedHashMap<>();
    private static final Map<String, String> PERFORM_BY_TEXT = new LinkedHashMap<>();
    private static final Map<String, String> CLOSE_TYPE_BY_TEXT = new LinkedHashMap<>();
    private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss")
    );

    static {
        TYPE_BY_TEXT.put("工伤", CaseStateMachine.TYPE_INJURY);
        TYPE_BY_TEXT.put("工伤案件", CaseStateMachine.TYPE_INJURY);
        TYPE_BY_TEXT.put("INJURY", CaseStateMachine.TYPE_INJURY);
        TYPE_BY_TEXT.put("仲裁", CaseStateMachine.TYPE_ARBITRATION);
        TYPE_BY_TEXT.put("劳动仲裁", CaseStateMachine.TYPE_ARBITRATION);
        TYPE_BY_TEXT.put("劳动仲裁案件", CaseStateMachine.TYPE_ARBITRATION);
        TYPE_BY_TEXT.put("ARBITRATION", CaseStateMachine.TYPE_ARBITRATION);

        RECOGNIZE_BY_TEXT.put("已认定", "RECOGNIZED");
        RECOGNIZE_BY_TEXT.put("已认定（工伤）", "RECOGNIZED");
        RECOGNIZE_BY_TEXT.put("认定工伤", "RECOGNIZED");
        RECOGNIZE_BY_TEXT.put("不予认定", "NOT_RECOGNIZED");
        RECOGNIZE_BY_TEXT.put("申报中", "DECLARING");
        RECOGNIZE_BY_TEXT.put("未申报", "NOT_DECLARED");

        RESULT_BY_TEXT.put("裁决", "AWARD");
        RESULT_BY_TEXT.put("裁决结案", "AWARD");
        RESULT_BY_TEXT.put("调解", "MEDIATION");
        RESULT_BY_TEXT.put("调解结案", "MEDIATION");
        RESULT_BY_TEXT.put("撤诉", "WITHDRAW");

        PERFORM_BY_TEXT.put("待履行", "PENDING");
        PERFORM_BY_TEXT.put("履行中", "DOING");
        PERFORM_BY_TEXT.put("已履行", "DONE");
        PERFORM_BY_TEXT.put("逾期未履行", "OVERDUE");

        CLOSE_TYPE_BY_TEXT.put("待遇结清", "TREATMENT_SETTLED");
        CLOSE_TYPE_BY_TEXT.put("调解结案", "MEDIATION");
        CLOSE_TYPE_BY_TEXT.put("裁决结案", "AWARD");
        CLOSE_TYPE_BY_TEXT.put("撤诉", "WITHDRAW");
        CLOSE_TYPE_BY_TEXT.put("其他", "OTHER");
    }

    private final CaseService caseService;
    private final CaseMainMapper caseMainMapper;
    private final SysUserMapper userMapper;
    private final WorkflowFlowService flowService;

    public CaseImportService(CaseService caseService, CaseMainMapper caseMainMapper,
                             SysUserMapper userMapper, WorkflowFlowService flowService) {
        this.caseService = caseService;
        this.caseMainMapper = caseMainMapper;
        this.userMapper = userMapper;
        this.flowService = flowService;
    }

    /* ==================== 模板下载 ==================== */

    public void writeTemplate(HttpServletResponse response) throws IOException {
        String fileName = URLEncoder.encode("历史案件导入模板.xlsx", "UTF-8").replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        List<List<String>> head = new ArrayList<>();
        int[] widths = new int[CaseImportColumns.all().size()];
        for (int i = 0; i < CaseImportColumns.all().size(); i++) {
            CaseImportColumns.Col col = CaseImportColumns.all().get(i);
            head.add(Collections.singletonList(col.getLabel() + (col.isRequired() ? "*" : "")));
            widths[i] = col.getWidth();
        }

        List<List<Object>> rows = new ArrayList<>();
        rows.add(demoRow("工伤"));
        rows.add(demoRow("仲裁"));

        List<List<String>> noteHead = new ArrayList<>();
        for (String title : new String[]{"字段", "是否必填", "填写说明", "示例"}) {
            noteHead.add(Collections.singletonList(title));
        }
        List<List<Object>> notes = new ArrayList<>();
        for (CaseImportColumns.Col col : CaseImportColumns.all()) {
            notes.add(Arrays.asList(col.getLabel(), col.isRequired() ? "必填" : "选填", col.getHint(), col.getDemo()));
        }
        notes.add(Arrays.asList("导入规则", "—",
                "整批预检：任意一行有错都不会写入，按提示修正后重新上传；案号沿用历史值且不能与系统已有案号重复",
                "—"));

        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            WriteSheet dataSheet = EasyExcel.writerSheet(0, "案件导入")
                    .head(head)
                    .registerWriteHandler(new SimpleColumnWidthStyleStrategy(18))
                    .registerWriteHandler(new SimpleRowHeightStyleStrategy((short) 20, (short) 20))
                    .build();
            writer.write(rows, dataSheet);

            WriteSheet noteSheet = EasyExcel.writerSheet(1, "填写说明")
                    .head(noteHead)
                    .registerWriteHandler(new SimpleColumnWidthStyleStrategy(26))
                    .build();
            writer.write(notes, noteSheet);
        } finally {
            writer.finish();
        }
    }

    private List<Object> demoRow(String type) {
        Map<String, String> demo = new LinkedHashMap<>();
        for (CaseImportColumns.Col col : CaseImportColumns.all()) {
            demo.put(col.getKey(), col.getDemo());
        }
        if (CaseStateMachine.TYPE_ARBITRATION.equals(TYPE_BY_TEXT.getOrDefault(type, ""))) {
            demo.put("caseType", "仲裁");
            demo.put("caseNo", "ZS2025-001");
            demo.put("employeeName", "王五");
            demo.put("currentNode", "履行");
            demo.put("occurDate", "2025-02-18");
            demo.put("description", "因加班费与调岗争议申请仲裁");
            demo.put("injuryReason", "");
            demo.put("recognize", "");
            demo.put("appraisalLevel", "");
            demo.put("restDays", "");
            demo.put("subsidyDisability", "");
            demo.put("subsidyMedical", "");
            demo.put("subsidyEmployment", "");
            demo.put("closeType", "裁决结案");
        } else {
            demo.put("arbitrationNo", "");
            demo.put("hearingDate", "");
            demo.put("resultType", "");
            demo.put("awardAmount", "");
            demo.put("performStatus", "");
            demo.put("performAmount", "");
        }
        List<Object> row = new ArrayList<>();
        for (CaseImportColumns.Col col : CaseImportColumns.all()) {
            row.add(demo.get(col.getKey()));
        }
        return row;
    }

    /* ==================== 预检 / 导入 ==================== */

    public Map<String, Object> preview(MultipartFile file) {
        List<CaseImportRow> rows = parse(file);
        Map<String, Object> result = buildResult(rows);
        log.info("案件导入预检: 共 {} 行，可导入 {} 行", result.get("total"), result.get("importable"));
        return result;
    }

    public Map<String, Object> confirm(MultipartFile file) {
        List<CaseImportRow> rows = parse(file);
        Map<String, Object> result = buildResult(rows);
        int failed = (int) result.get("failed");
        if (failed > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("还有 %d 行未通过校验，请按预检提示修正后重新上传", failed));
        }
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件中没有可导入的数据");
        }
        List<Long> ids = new ArrayList<>(rows.size());
        for (CaseImportRow row : rows) {
            ids.add(caseService.importCase(row));
        }
        log.info("案件导入完成: {} 条，操作人={}", ids.size(), com.gsms.common.UserContext.get().getUserId());
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("imported", ids.size());
        done.put("caseIds", ids);
        return done;
    }

    private Map<String, Object> buildResult(List<CaseImportRow> rows) {
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> previewRows = new ArrayList<>();
        int failed = 0;
        for (CaseImportRow row : rows) {
            if (!row.getErrors().isEmpty()) {
                failed++;
                for (String message : row.getErrors()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("row", row.getRowNum());
                    item.put("caseNo", row.getCaseNo());
                    item.put("message", message);
                    errors.add(item);
                }
            }
            if (previewRows.size() < 20) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("row", row.getRowNum());
                item.put("caseType", CaseDict.typeName(row.getCaseType()));
                item.put("caseNo", row.getCaseNo());
                item.put("employeeName", row.getEmployeeName());
                item.put("statusName", row.getStatus() == null ? row.getCurrentNodeText() : CaseDict.statusName(row.getStatus()));
                item.put("occurDate", row.getOccurDate());
                item.put("handlerId", row.getHandlerId());
                previewRows.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("importable", rows.size() - failed);
        result.put("failed", failed);
        result.put("errors", errors);
        result.put("rows", previewRows);
        return result;
    }

    /* ==================== 解析 ==================== */

    private List<CaseImportRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要导入的 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只支持 .xlsx / .xls 文件，请使用下载的模板");
        }

        RowListener listener = new RowListener();
        try {
            EasyExcel.read(file.getInputStream(), listener).sheet(0).headRowNumber(1).doRead();
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件读取失败：" + e.getMessage());
        }

        Map<Integer, String> headerMap = listener.header;
        if (headerMap.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未读到表头，请使用系统提供的导入模板");
        }
        Map<String, Integer> indexOfKey = new HashMap<>();
        for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
            String key = CaseImportColumns.keyOf(entry.getValue());
            if (key != null) {
                indexOfKey.putIfAbsent(key, entry.getKey());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String key : new String[]{"caseType", "caseNo", "employeeName", "occurDate", "currentNode", "handler"}) {
            if (!indexOfKey.containsKey(key)) {
                missing.add(labelOf(key));
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "模板缺少必需列：" + String.join("、", missing) + "，请重新下载模板填写");
        }
        if (listener.rows.size() > MAX_ROWS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("单次最多导入 %d 行，请拆分后分批导入", MAX_ROWS));
        }

        // 用户表与流程节点一次性载入，避免逐行查库
        List<SysUser> allUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>());
        Map<String, SysUser> users = allUsers.stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));
        Map<String, List<SysUser>> usersByName = allUsers.stream()
                .collect(Collectors.groupingBy(SysUser::getUserName));
        Map<String, Map<String, String>> nodeNameCache = new HashMap<>();

        List<CaseImportRow> result = new ArrayList<>(listener.rows.size());
        Set<String> seenCaseNo = new HashSet<>();
        for (RawRow rawRow : listener.rows) {
            Map<Integer, String> raw = rawRow.cells;
            if (isBlankRow(raw)) {
                continue;
            }
            CaseImportRow row = new CaseImportRow();
            row.setRowNum(rawRow.rowNum);
            row.setCaseTypeText(value(raw, indexOfKey.get("caseType")));
            row.setCaseNo(value(raw, indexOfKey.get("caseNo")));
            row.setEmployeeName(value(raw, indexOfKey.get("employeeName")));
            row.setPlantArea(value(raw, indexOfKey.get("plantArea")));
            row.setWorkshopTeam(value(raw, indexOfKey.get("workshopTeam")));
            row.setLeaderL1(value(raw, indexOfKey.get("leaderL1")));
            row.setLeaderL2(value(raw, indexOfKey.get("leaderL2")));
            row.setOccurDateText(value(raw, indexOfKey.get("occurDate")));
            row.setCurrentNodeText(value(raw, indexOfKey.get("currentNode")));
            row.setHandlerText(value(raw, indexOfKey.get("handler")));
            row.setArchivedText(value(raw, indexOfKey.get("archived")));
            row.setCloseDateText(value(raw, indexOfKey.get("closeDate")));
            row.setCloseTypeText(value(raw, indexOfKey.get("closeType")));
            row.setDescription(value(raw, indexOfKey.get("description")));
            row.setInjuryReason(value(raw, indexOfKey.get("injuryReason")));
            row.setRecognizeText(value(raw, indexOfKey.get("recognize")));
            row.setAppraisalLevel(value(raw, indexOfKey.get("appraisalLevel")));
            row.setRestDaysText(value(raw, indexOfKey.get("restDays")));
            row.setArbitrationNo(value(raw, indexOfKey.get("arbitrationNo")));
            row.setHearingDateText(value(raw, indexOfKey.get("hearingDate")));
            row.setResultTypeText(value(raw, indexOfKey.get("resultType")));
            row.setAwardAmountText(value(raw, indexOfKey.get("awardAmount")));
            row.setPerformStatusText(value(raw, indexOfKey.get("performStatus")));
            row.setPerformAmountText(value(raw, indexOfKey.get("performAmount")));
            row.setSubsidyDisabilityText(value(raw, indexOfKey.get("subsidyDisability")));
            row.setSubsidyMedicalText(value(raw, indexOfKey.get("subsidyMedical")));
            row.setSubsidyEmploymentText(value(raw, indexOfKey.get("subsidyEmployment")));

            validate(row, users, usersByName, nodeNameCache, seenCaseNo);
            result.add(row);
        }
        return result;
    }

    private void validate(CaseImportRow row, Map<String, SysUser> users, Map<String, List<SysUser>> usersByName,
                          Map<String, Map<String, String>> nodeNameCache,
                          Set<String> seenCaseNo) {
        List<String> errors = row.getErrors();

        String typeText = row.getCaseTypeText();
        String caseType = typeText == null ? null : TYPE_BY_TEXT.get(typeText.trim().toUpperCase());
        if (caseType == null) {
            errors.add("案件类型只能是「工伤」或「仲裁」");
        } else {
            row.setCaseType(caseType);
        }

        if (!StringUtils.hasText(row.getCaseNo())) {
            errors.add("案号不能为空");
        } else if (!seenCaseNo.add(row.getCaseNo().trim())) {
            errors.add("案号「" + row.getCaseNo() + "」在文件中重复");
        } else {
            Long exists = caseMainMapper.selectCount(new LambdaQueryWrapper<CaseMain>()
                    .eq(CaseMain::getCaseNo, row.getCaseNo().trim()));
            if (exists != null && exists > 0) {
                errors.add("案号「" + row.getCaseNo() + "」系统中已存在");
            }
            row.setCaseNo(row.getCaseNo().trim());
        }

        if (!StringUtils.hasText(row.getEmployeeName())) {
            errors.add("当事人姓名不能为空");
        }

        if (!StringUtils.hasText(row.getOccurDateText())) {
            errors.add("发生日期不能为空");
        } else {
            LocalDate occur = parseDate(row.getOccurDateText());
            if (occur == null) {
                errors.add("发生日期格式无法识别（建议 yyyy-MM-dd）：" + row.getOccurDateText());
            } else if (occur.isAfter(LocalDate.now())) {
                errors.add("发生日期不能晚于今天");
            } else {
                row.setOccurDate(occur);
            }
        }

        if (!StringUtils.hasText(row.getHandlerText())) {
            errors.add("承办人不能为空");
        } else {
            String text = row.getHandlerText().trim();
            SysUser user = users.get(text);
            if (user == null) {
                List<SysUser> byName = usersByName.get(text);
                if (byName != null && byName.size() == 1) {
                    user = byName.get(0);
                } else if (byName != null && byName.size() > 1) {
                    errors.add("承办人「" + text + "」存在重名，请填登录名");
                }
            }
            if (user == null) {
                if (!errors.contains("承办人「" + text + "」存在重名，请填登录名")) {
                    errors.add("承办人「" + text + "」在系统中不存在");
                }
            } else {
                row.setHandlerId(user.getUserId());
            }
        }

        // 节点：既认节点名，也认状态码
        String nodeText = row.getCurrentNodeText();
        if (!StringUtils.hasText(nodeText)) {
            errors.add("当前节点不能为空");
        } else if (row.getCaseType() != null) {
            Map<String, String> byName = nodeNameCache.computeIfAbsent(row.getCaseType(), this::nodeNameToCode);
            String key = nodeText.replace(" ", "").trim();
            String code = byName.get(key);
            if (code == null) {
                code = byName.get(key.toUpperCase());
            }
            if (code == null) {
                errors.add("当前节点「" + nodeText + "」不在" + CaseDict.typeName(row.getCaseType())
                        + "的流程中，可选：" + String.join("/", nodeNames(row.getCaseType())));
            } else {
                row.setStatus(code);
            }
        }

        boolean archived = "是".equals(trim(row.getArchivedText()))
                || "1".equals(trim(row.getArchivedText()))
                || "Y".equalsIgnoreCase(trim(row.getArchivedText()))
                || "TRUE".equalsIgnoreCase(trim(row.getArchivedText()));
        if (archived) {
            row.setStatus(CaseStateMachine.ARCHIVED);
        }
        row.setArchiveFlag(archived || CaseStateMachine.ARCHIVED.equals(row.getStatus()));
        if (row.isArchiveFlag()) {
            row.setStatus(CaseStateMachine.ARCHIVED);
        }

        if (StringUtils.hasText(row.getCloseDateText())) {
            LocalDate close = parseDate(row.getCloseDateText());
            if (close == null) {
                errors.add("结案日期格式无法识别：" + row.getCloseDateText());
            } else {
                row.setCloseDate(close);
            }
        }
        if (StringUtils.hasText(row.getCloseTypeText())) {
            String code = CLOSE_TYPE_BY_TEXT.get(trim(row.getCloseTypeText()));
            if (code == null) {
                errors.add("结案方式只能是：待遇结清/调解结案/裁决结案/撤诉/其他");
            } else {
                row.setCloseType(code);
            }
        }

        if (StringUtils.hasText(row.getRecognizeText())) {
            String code = RECOGNIZE_BY_TEXT.get(trim(row.getRecognizeText()));
            if (code == null) {
                errors.add("工伤认定情况只能是：已认定/不予认定/申报中/未申报");
            } else {
                row.setRecognizeCode(code);
            }
        }
        if (StringUtils.hasText(row.getResultTypeText())) {
            String code = RESULT_BY_TEXT.get(trim(row.getResultTypeText()));
            if (code == null) {
                errors.add("裁决/调解结果只能是：裁决/调解/撤诉");
            } else {
                row.setResultCode(code);
            }
        }
        if (StringUtils.hasText(row.getPerformStatusText())) {
            String code = PERFORM_BY_TEXT.get(trim(row.getPerformStatusText()));
            if (code == null) {
                errors.add("履行状态只能是：待履行/履行中/已履行/逾期未履行");
            } else {
                row.setPerformCode(code);
            }
        }
        if (StringUtils.hasText(row.getHearingDateText())) {
            LocalDateTime time = parseDateTime(row.getHearingDateText());
            if (time == null) {
                errors.add("开庭时间格式无法识别（建议 yyyy-MM-dd HH:mm）：" + row.getHearingDateText());
            } else {
                row.setHearingDate(time);
            }
        }

        row.setRestDays(parseInt(row.getRestDaysText(), "休假天数", errors));
        row.setAwardAmount(parseAmount(row.getAwardAmountText(), "裁决/调解金额", errors));
        row.setPerformAmount(parseAmount(row.getPerformAmountText(), "履行款", errors));
        row.setSubsidyDisabilityAmount(parseAmount(row.getSubsidyDisabilityText(), "一次性伤残金金额", errors));
        row.setSubsidyMedicalAmount(parseAmount(row.getSubsidyMedicalText(), "一次性医疗金金额", errors));
        row.setSubsidyEmploymentAmount(parseAmount(row.getSubsidyEmploymentText(), "一次性就业金金额", errors));
    }

    /** 流程节点名称，按流程顺序返回（不含状态码别名），用于报错提示 */
    private List<String> nodeNames(String caseType) {
        List<String> names = new ArrayList<>();
        for (WfNodeDef node : flowService.enabledNodes(caseType)) {
            if (!names.contains(node.getNodeName())) {
                names.add(node.getNodeName());
            }
        }
        return names;
    }

    private Map<String, String> nodeNameToCode(String caseType) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (WfNodeDef node : flowService.enabledNodes(caseType)) {
            byName.put(node.getNodeName().replace(" ", ""), node.getStatusCode());
            byName.putIfAbsent(node.getStatusCode(), node.getStatusCode());
        }
        return byName;
    }

    /* ==================== 工具方法 ==================== */

    private String labelOf(String key) {
        for (CaseImportColumns.Col col : CaseImportColumns.all()) {
            if (col.getKey().equals(key)) {
                return col.getLabel();
            }
        }
        return key;
    }

    private String value(Map<Integer, String> raw, Integer index) {
        if (index == null) {
            return null;
        }
        String text = raw.get(index);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private boolean isBlankRow(Map<Integer, String> raw) {
        for (String text : raw.values()) {
            if (StringUtils.hasText(text)) {
                return false;
            }
        }
        return true;
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private Integer parseInt(String text, String field, List<String> errors) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "").trim()).intValue();
        } catch (NumberFormatException e) {
            errors.add(field + "必须是数字：" + text);
            return null;
        }
    }

    private BigDecimal parseAmount(String text, String field, List<String> errors) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String cleaned = text.replace("￥", "").replace("¥", "").replace(",", "")
                .replace("元", "").replace(" ", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            errors.add(field + "必须是金额数字：" + text);
            return null;
        }
    }

    /** 兼容 Excel 日期序列号（如 45724）与常见文本格式 */
    LocalDate parseDate(String text) {
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.matches("\\d{5}(\\.0)?")) {
            long serial = (long) Double.parseDouble(value);
            return LocalDate.of(1899, 12, 30).plusDays(serial);
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (Exception ignored) {
                // 继续尝试下一种格式
            }
        }
        // Excel 常把日期存成 "2025-03-08 00:00:00"
        if (value.length() >= 10) {
            String head = value.substring(0, 10).replace('/', '-').replace('.', '-');
            try {
                return LocalDate.parse(head, DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    LocalDateTime parseDateTime(String text) {
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 继续尝试下一种格式
            }
        }
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    /** 一行原始数据 + 它在 Excel 里的行号（报错时指给用户看） */
    private static class RawRow {
        private final int rowNum;
        private final Map<Integer, String> cells;

        RawRow(int rowNum, Map<Integer, String> cells) {
            this.rowNum = rowNum;
            this.cells = cells;
        }
    }

    /** 逐行收集原始值；表头单独保存用于列名映射 */
    private static class RowListener extends AnalysisEventListener<Map<Integer, String>> {
        private final Map<Integer, String> header = new LinkedHashMap<>();
        private final List<RawRow> rows = new ArrayList<>();

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            header.putAll(headMap);
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            int index = context.readRowHolder() == null ? rows.size() + 1 : context.readRowHolder().getRowIndex();
            rows.add(new RawRow(index + 1, new LinkedHashMap<>(data)));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 数据已在 invoke 中收集
        }
    }
}
