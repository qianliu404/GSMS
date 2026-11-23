package com.gsms.module.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.module.notify.dto.ConditionFieldVO;
import com.gsms.module.notify.dto.NotifyCondition;
import com.gsms.module.notify.dto.NotifyPreviewVO;
import com.gsms.module.notify.entity.NotifyConfig;
import com.gsms.module.notify.entity.NotifyHistory;
import com.gsms.module.notify.mapper.NotifyConfigMapper;
import com.gsms.module.notify.mapper.NotifyQueryMapper;
import com.gsms.module.notify.mapper.NotifyHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 推送设置核心：配置管理、消息预览、定时推送。
 * 预览与实际推送共用 buildPreview / buildContent，确保「所见即所得」。
 */
@Slf4j
@Service
public class NotifyService {

    /** 可选字段：编码 -> 显示名 */
    public static final LinkedHashMap<String, String> FIELD_LABELS = new LinkedHashMap<>();
    static {
        FIELD_LABELS.put("caseNo", "案号");
        FIELD_LABELS.put("caseName", "案件名称");
        FIELD_LABELS.put("caseType", "案件类型");
        FIELD_LABELS.put("nodeName", "当前节点");
        FIELD_LABELS.put("deadlineDate", "截止日期");
        FIELD_LABELS.put("remainDays", "剩余天数");
        FIELD_LABELS.put("handlerName", "办理人");
        FIELD_LABELS.put("deadlineTypeName", "期限类型");
        FIELD_LABELS.put("employeeName", "当事人");
        FIELD_LABELS.put("deptName", "责任部门");
        FIELD_LABELS.put("plantArea", "厂区");
        FIELD_LABELS.put("workshopTeam", "车间班组");
        FIELD_LABELS.put("occurDate", "发生日期");
        FIELD_LABELS.put("statusName", "期限状态");
    }
    public static final String DEFAULT_FIELDS = "caseNo,caseName,nodeName,deadlineDate,remainDays,handlerName";

    /** 条件字段元数据——新增条件只需在此加一条 + NotifyQueryMapper.xml 的 choose 分支 */
    public static final List<ConditionFieldVO> CONDITION_FIELDS = new ArrayList<>();
    static {
        CONDITION_FIELDS.add(field("caseNo", "案号", "TEXT", "EQ", "NE", "CONTAINS"));
        ConditionFieldVO caseType = field("caseType", "案件类型", "ENUM", "EQ", "NE", "IN", "NOT_IN");
        caseType.getOptions().add(new ConditionFieldVO.Option("INJURY", "工伤"));
        caseType.getOptions().add(new ConditionFieldVO.Option("ARBITRATION", "劳动仲裁"));
        ConditionFieldVO status = field("status", "案件状态", "ENUM", "EQ", "NE", "IN", "NOT_IN");
        String[][] statuses = {
                {"DRAFT", "草稿"}, {"FILED", "立案"}, {"INVESTIGATION", "调查取证"},
                {"APPRAISAL", "鉴定"}, {"DECLARE", "申报"}, {"RECOGNITION", "认定"},
                {"ARBITRATION", "仲裁"}, {"LITIGATION", "诉讼"}, {"PERFORM", "履行"},
                {"CLOSED", "结案"}
        };
        for (String[] s : statuses) status.getOptions().add(new ConditionFieldVO.Option(s[0], s[1]));
        CONDITION_FIELDS.add(caseType);
        CONDITION_FIELDS.add(status);
        CONDITION_FIELDS.add(field("nodeCode", "当前节点编码", "ENUM", "EQ", "NE", "IN", "NOT_IN"));
        CONDITION_FIELDS.add(field("nodeName", "当前节点名称", "TEXT", "EQ", "CONTAINS"));
        ConditionFieldVO dtype = field("deadlineType", "期限类型", "ENUM", "EQ", "NE", "IN", "NOT_IN");
        dtype.getOptions().add(new ConditionFieldVO.Option("LEGAL", "法定"));
        dtype.getOptions().add(new ConditionFieldVO.Option("INTERNAL", "内部"));
        CONDITION_FIELDS.add(dtype);
        CONDITION_FIELDS.add(field("handlerId", "承办人工号", "USER", "EQ", "IN", "NOT_IN", "IS_EMPTY", "NOT_EMPTY"));
        CONDITION_FIELDS.add(field("handlerName", "承办人姓名", "TEXT", "EQ", "CONTAINS"));
        CONDITION_FIELDS.add(field("deptName", "责任部门", "TEXT", "EQ", "CONTAINS"));
        CONDITION_FIELDS.add(field("plantArea", "厂区", "TEXT", "EQ", "NE", "CONTAINS", "IS_EMPTY", "NOT_EMPTY"));
        CONDITION_FIELDS.add(field("workshopTeam", "车间班组", "TEXT", "EQ", "CONTAINS"));
        ConditionFieldVO started = field("started", "是否已开启计时", "ENUM", "EQ", "NE");
        started.getOptions().add(new ConditionFieldVO.Option("1", "已开启"));
        started.getOptions().add(new ConditionFieldVO.Option("0", "未开启"));
        CONDITION_FIELDS.add(started);
        CONDITION_FIELDS.add(field("remainDays", "距截止天数", "NUMBER", "GT", "GTE", "LT", "LTE"));
        CONDITION_FIELDS.add(field("overdueLevel", "超期等级", "NUMBER", "EQ", "GT", "GTE", "LT", "LTE"));
        CONDITION_FIELDS.add(field("deadlineDate", "截止日期", "DATE", "EQ", "GT", "GTE", "LT", "LTE"));
        CONDITION_FIELDS.add(field("occurDate", "发生日期", "DATE", "EQ", "GT", "GTE", "LT", "LTE"));
    }

    private static ConditionFieldVO field(String code, String label, String type, String... ops) {
        ConditionFieldVO f = new ConditionFieldVO();
        f.setCode(code); f.setLabel(label); f.setType(type);
        f.setOperators(Arrays.asList(ops));
        return f;
    }

    /** 默认条件：未结案 + 未完成的当前节点期限 + 距截止 7 天内 */
    public static List<NotifyCondition> defaultConditions() {
        List<NotifyCondition> list = new ArrayList<>();
        list.add(cond("status", "NE", "CLOSED"));
        list.add(cond("deadlineType", "IN", null, "LEGAL", "INTERNAL"));
        list.add(cond("remainDays", "LTE", "7"));
        return list;
    }

    private static NotifyCondition cond(String field, String op, String value, String... values) {
        NotifyCondition c = new NotifyCondition();
        c.setField(field); c.setOp(op); c.setValue(value);
        if (values != null && values.length > 0) c.setValues(Arrays.asList(values));
        return c;
    }

    private final NotifyConfigMapper configMapper;
    private final NotifyHistoryMapper historyMapper;
    private final NotifyQueryMapper queryMapper;
    private final NotifyImageRenderer imageRenderer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gsms.notify.webhook:}")
    private String defaultWebhook;

    public NotifyService(NotifyConfigMapper configMapper, NotifyHistoryMapper historyMapper,
                         NotifyQueryMapper queryMapper, NotifyImageRenderer imageRenderer) {
        this.configMapper = configMapper;
        this.historyMapper = historyMapper;
        this.queryMapper = queryMapper;
        this.imageRenderer = imageRenderer;
    }

    /** 条件字段元数据，供前端渲染下拉框 */
    public List<ConditionFieldVO> conditionFields() { return CONDITION_FIELDS; }

    /* ------------------------- 配置 CRUD ------------------------- */

    public List<NotifyConfig> list() {
        return configMapper.selectList(Wrappers.<NotifyConfig>lambdaQuery().orderByAsc(NotifyConfig::getId));
    }

    public NotifyConfig get(Long id) {
        NotifyConfig c = configMapper.selectById(id);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND, "推送配置不存在");
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public NotifyConfig save(NotifyConfig config) {
        validate(config);
        if (config.getId() == null) {
            config.setCreateTime(LocalDateTime.now());
            configMapper.insert(config);
        } else {
            get(config.getId());
            configMapper.updateById(config);
        }
        return config;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        get(id);
        configMapper.deleteById(id);
    }

    /** 切换启用状态 */
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Integer enabled) {
        NotifyConfig c = get(id);
        c.setEnabled(enabled);
        configMapper.updateById(c);
    }

    private void validate(NotifyConfig c) {
        if (c.getConfigName() == null || c.getConfigName().trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写配置名称");
        }
        if (c.getCycleType() == null || !Arrays.asList("DAILY", "WEEKLY", "MONTHLY").contains(c.getCycleType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送周期无效");
        }
        if (c.getAheadDays() != null && (c.getAheadDays() < 1 || c.getAheadDays() > 365)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "提前天数应在 1-365 之间");
        }
        if (c.getPushTime() != null && !c.getPushTime().matches("\\d{2}:\\d{2}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送时间格式应为 HH:mm");
        }
        if ("WEEKLY".equals(c.getCycleType()) && (c.getWeekDays() == null || c.getWeekDays().trim().isEmpty())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "周推送需选择星期");
        }
        if ("MONTHLY".equals(c.getCycleType()) && (c.getMonthDays() == null || c.getMonthDays().trim().isEmpty())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月推送需选择日期");
        }
        if (c.getFields() == null || c.getFields().trim().isEmpty()) {
            c.setFields(DEFAULT_FIELDS);
        }
        if (c.getConditions() != null && !c.getConditions().trim().isEmpty()) {
            parseConditions(c.getConditions()); // 校验 JSON 合法性
        }
        if (c.getAheadDays() == null) c.setAheadDays(7);
        if (c.getIncludeOverdue() == null) c.setIncludeOverdue(1);
        if (c.getEnabled() == null) c.setEnabled(1);
    }

    /* ------------------------- 预览 ------------------------- */

    /** 按临时参数预览（前端未保存时也能预览） */
    public NotifyPreviewVO preview(NotifyConfig config) {
        if (config.getFields() == null || config.getFields().trim().isEmpty()) config.setFields(DEFAULT_FIELDS);
        List<NotifyCondition> conds = resolveConditions(config);
        List<Map<String, Object>> raw = queryMapper.selectByConditions(sanitize(conds));
        List<NotifyPreviewVO.FieldRow> rows = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            NotifyPreviewVO.FieldRow r = new NotifyPreviewVO.FieldRow();
            r.setCaseNo(str(m.get("caseNo")));
            r.setCaseName(str(m.get("caseName")));
            r.setNodeName(str(m.get("nodeName")));
            Object d = m.get("deadlineDate");
            r.setDeadlineDate(d == null ? "" : d.toString());
            Object rd = m.get("remainDays");
            r.setRemainDays(rd == null ? null : ((Number) rd).longValue());
            r.setHandlerName(str(m.get("handlerName")));
            r.setCaseType(str(m.get("caseType")));
            r.setDeadlineTypeName(str(m.get("deadlineTypeName")));
            r.setStatusName(str(m.get("statusName")));
            r.setEmployeeName(str(m.get("employeeName")));
            r.setDeptName(str(m.get("deptName")));
            r.setPlantArea(str(m.get("plantArea")));
            r.setWorkshopTeam(str(m.get("workshopTeam")));
            r.setOccurDate(str(m.get("occurDate")));
            rows.add(r);
        }
        return buildPreview(config, rows, raw);
    }

    /** 按已保存配置 ID 预览 */
    public NotifyPreviewVO previewById(Long id) {
        return preview(get(id));
    }

    /** 解析条件 JSON；为空时回退到默认条件（兼容旧配置） */
    private List<NotifyCondition> resolveConditions(NotifyConfig config) {
        if (config.getConditions() == null || config.getConditions().trim().isEmpty()) {
            return defaultConditions();
        }
        return parseConditions(config.getConditions());
    }

    private List<NotifyCondition> parseConditions(String json) {
        try {
            List<NotifyCondition> list = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, NotifyCondition.class));
            return list == null ? defaultConditions() : list;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送条件格式不合法");
        }
    }

    /** 白名单校验：过滤未知字段/操作符，避免 SQL 片段被拼接注入 */
    private List<NotifyCondition> sanitize(List<NotifyCondition> conds) {
        List<NotifyCondition> out = new ArrayList<>();
        for (NotifyCondition c : conds) {
            ConditionFieldVO meta = null;
            for (ConditionFieldVO f : CONDITION_FIELDS) if (f.getCode().equals(c.getField())) { meta = f; break; }
            if (meta == null || !meta.getOperators().contains(c.getOp())) continue;
            if (("IN".equals(c.getOp()) || "NOT_IN".equals(c.getOp()))) {
                List<String> vals = c.getValues();
                if (vals == null && c.getValue() != null) vals = Arrays.asList(c.getValue().split(","));
                if (vals == null || vals.isEmpty()) continue;
                List<String> clean = new ArrayList<>();
                for (String v : vals) if (v != null && !v.trim().isEmpty()) clean.add(v.trim());
                if (clean.isEmpty()) continue;
                c.setValues(clean);
            } else if (!"IS_EMPTY".equals(c.getOp()) && !"NOT_EMPTY".equals(c.getOp())) {
                if (c.getValue() == null || c.getValue().trim().isEmpty()) continue;
                c.setValue(c.getValue().trim());
            }
            out.add(c);
        }
        return out;
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }

    private NotifyPreviewVO buildPreview(NotifyConfig config, List<NotifyPreviewVO.FieldRow> rows,
                                         List<Map<String, Object>> raw) {
        NotifyPreviewVO vo = new NotifyPreviewVO();
        vo.setRows(rows);
        vo.setMatched(rows.size());
        List<NotifyPreviewVO.FieldMeta> fields = new ArrayList<>();
        Set<String> selected = parseFields(config.getFields());
        for (Map.Entry<String, String> e : FIELD_LABELS.entrySet()) {
            fields.add(new NotifyPreviewVO.FieldMeta(e.getKey(), e.getValue(), selected.contains(e.getKey())));
        }
        vo.setAvailableFields(fields);
        String title = config.getConfigName() == null ? "案件期限提醒" : config.getConfigName();
        String rule = buildRuleText(config);
        vo.setContent(buildContent(title, rule, rows, selected));
        // 用原始 map（含完整字段）渲染图片，图片字段顺序与用户勾选一致
        List<String> order = new ArrayList<>(selected);
        byte[] png = imageRenderer.render(title, rule, raw, order);
        vo.setImageBase64(imageRenderer.toDataUrl(png));
        return vo;
    }

    /** 规则描述：由条件生成，直观展示当前筛选口径 */
    private String buildRuleText(NotifyConfig config) {
        List<NotifyCondition> conds = resolveConditions(config);
        List<String> parts = new ArrayList<>();
        for (NotifyCondition c : sanitize(conds)) {
            String label = c.getField();
            for (ConditionFieldVO f : CONDITION_FIELDS) if (f.getCode().equals(c.getField())) { label = f.getLabel(); break; }
            String v;
            if ("IN".equals(c.getOp()) || "NOT_IN".equals(c.getOp())) {
                List<String> vs = new ArrayList<>();
                for (String x : c.getValues()) vs.add(optionLabel(c.getField(), x));
                v = String.join("/", vs);
            } else {
                v = optionLabel(c.getField(), c.getValue());
            }
            parts.add(label + " " + opLabel(c.getOp()) + " " + (v == null ? "" : v));
        }
        return parts.isEmpty() ? "全部案件" : String.join("；", parts);
    }

    /** 枚举字段的值转中文标签（规则文案更直观） */
    private String optionLabel(String fieldCode, String value) {
        if (value == null) return "";
        for (ConditionFieldVO f : CONDITION_FIELDS) {
            if (f.getCode().equals(fieldCode)) {
                for (ConditionFieldVO.Option o : f.getOptions()) {
                    if (o.getValue().equals(value)) return o.getLabel();
                }
            }
        }
        return value;
    }

    private String opLabel(String op) {
        switch (op) {
            case "EQ": return "=";
            case "NE": return "≠";
            case "CONTAINS": return "包含";
            case "IN": return "属于";
            case "NOT_IN": return "不属于";
            case "GT": return ">";
            case "GTE": return "≥";
            case "LT": return "<";
            case "LTE": return "≤";
            case "IS_EMPTY": return "为空";
            case "NOT_EMPTY": return "不为空";
            default: return op;
        }
    }

    private Set<String> parseFields(String fields) {
        Set<String> set = new LinkedHashSet<>();
        for (String s : fields.split(",")) {
            String f = s.trim();
            if (FIELD_LABELS.containsKey(f)) set.add(f);
        }
        if (set.isEmpty()) set.addAll(FIELD_LABELS.keySet());
        return set;
    }

    /** 生成 Markdown 正文（兼容旧版文本预览） */
    private String buildContent(String title, String rule, List<NotifyPreviewVO.FieldRow> rows, Set<String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(title).append("\n");
        sb.append("> 统计时间：").append(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
        sb.append("> 规则：").append(rule).append("\n\n");
        if (rows.isEmpty()) {
            sb.append("当前无符合条件的案件。");
            return sb.toString();
        }
        sb.append("**共 ").append(rows.size()).append(" 件：**\n\n");
        int i = 0;
        for (NotifyPreviewVO.FieldRow r : rows) {
            i++;
            sb.append("**").append(i).append(". ");
            List<String> head = new ArrayList<>();
            for (String f : fields) {
                if ("caseNo".equals(f) || "caseName".equals(f)) head.add(fieldValue(r, f));
            }
            sb.append(String.join(" ", head)).append("**\n> ");
            List<String> detail = new ArrayList<>();
            for (String f : fields) {
                String v = fieldValue(r, f);
                if (v == null || v.isEmpty()) continue;
                if ("caseNo".equals(f) || "caseName".equals(f)) continue;
                detail.add(FIELD_LABELS.get(f) + ":" + v);
            }
            sb.append(String.join("  |  ", detail)).append("\n\n");
        }
        return sb.toString();
    }

    private String fieldValue(NotifyPreviewVO.FieldRow r, String field) {
        switch (field) {
            case "caseNo": return r.getCaseNo();
            case "caseName": return r.getCaseName();
            case "caseType": return "INJURY".equals(r.getCaseType()) ? "工伤" : "ARBITRATION".equals(r.getCaseType()) ? "劳动仲裁" : r.getCaseType();
            case "nodeName": return r.getNodeName();
            case "deadlineDate": return r.getDeadlineDate();
            case "remainDays":
                if (r.getRemainDays() == null) return "";
                long d = r.getRemainDays();
                if (d < 0) return "超期 " + (-d) + " 天";
                if (d == 0) return "今日";
                return d + " 天";
            case "handlerName": return r.getHandlerName();
            case "deadlineTypeName": return r.getDeadlineTypeName();
            case "statusName": return r.getStatusName();
            case "employeeName": return r.getEmployeeName();
            case "deptName": return r.getDeptName();
            case "plantArea": return r.getPlantArea();
            case "workshopTeam": return r.getWorkshopTeam();
            case "occurDate": return r.getOccurDate();
            default: return "";
        }
    }

    /* ------------------------- 发送 ------------------------- */

    /** 立即测试推送（不写 lastPushTime，不判断周期） */
    @Transactional(rollbackFor = Exception.class)
    public int testPush(Long id) {
        NotifyConfig config = get(id);
        try {
            return doPush(config, true);
        } catch (Exception e) {
            if (e instanceof BizException) throw (BizException) e;
            throw new BizException(ErrorCode.SYSTEM_ERROR, "推送失败：" + e.getMessage());
        }
    }

    /** 定时入口：按周期与推送时间判断是否需要推送 */
    public void scheduledPush() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<NotifyConfig> configs;
        try {
            configs = configMapper.selectEnabled();
        } catch (Exception e) {
            log.error("读取推送配置失败", e);
            return;
        }
        for (NotifyConfig c : configs) {
            try {
                if (!shouldPushToday(c, today)) continue;
                // 只在推送时间所在分钟内触发，避免每分钟重复推送
                LocalTime pushAt = parseTime(c.getPushTime());
                if (pushAt == null || Math.abs(now.getHour() * 60 + now.getMinute() - (pushAt.getHour() * 60 + pushAt.getMinute())) > 1) {
                    continue;
                }
                doPush(c, false);
            } catch (Exception e) {
                log.error("推送配置[{}]执行失败", c.getId(), e);
                recordHistory(c, 0, "FAIL", null, e.getMessage());
            }
        }
    }

    /** 供运维手动触发全部启用配置（不受时间窗口限制） */
    public Map<String, Integer> pushAll() {
        int success = 0, fail = 0;
        for (NotifyConfig c : configMapper.selectEnabled()) {
            try {
                doPush(c, false);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("推送配置[{}]执行失败", c.getId(), e);
            }
        }
        Map<String, Integer> r = new HashMap<>();
        r.put("success", success);
        r.put("fail", fail);
        return r;
    }

    private boolean shouldPushToday(NotifyConfig c, LocalDate today) {
        if ("DAILY".equals(c.getCycleType())) return true;
        if ("WEEKLY".equals(c.getCycleType())) {
            for (String s : c.getWeekDays().split(",")) {
                if (s.trim().equals(String.valueOf(today.getDayOfWeek().getValue()))) return true;
            }
            return false;
        }
        if ("MONTHLY".equals(c.getCycleType())) {
            for (String s : c.getMonthDays().split(",")) {
                if (s.trim().equals(String.valueOf(today.getDayOfMonth()))) return true;
            }
            // 月末兜底：配置 31 但当月只有 30 天
            if (c.getMonthDays().contains("31") && today.getDayOfMonth() == today.lengthOfMonth()) return true;
            return false;
        }
        return false;
    }

    private LocalTime parseTime(String s) {
        try {
            if (s == null) return null;
            String[] p = s.split(":");
            return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private int doPush(NotifyConfig config, boolean isTest) throws Exception {
        NotifyPreviewVO preview = preview(config);
        String webhook = config.getWebhook() != null && !config.getWebhook().trim().isEmpty()
                ? config.getWebhook() : defaultWebhook;
        if (webhook == null || webhook.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未配置企业微信群机器人 webhook");
        }
        // 从 data URL 还原图片字节，计算 md5 后以 image 类型发送
        String dataUrl = preview.getImageBase64();
        String b64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
        byte[] png = Base64.getDecoder().decode(b64);
        String result;
        try {
            result = sendWebhookImage(webhook, png);
            if (!"ok".equalsIgnoreCase(result)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "企业微信返回：" + result);
            }
        } catch (Exception e) {
            recordHistory(config, preview.getMatched(), "FAIL", preview.getContent(), e.getMessage());
            throw e;
        }
        recordHistory(config, preview.getMatched(), "SUCCESS", preview.getContent(), null);
        if (!isTest) {
            NotifyConfig c = get(config.getId());
            c.setLastPushTime(LocalDateTime.now());
            configMapper.updateById(c);
        }
        log.info("推送配置[{}]完成，命中 {} 件", config.getId(), preview.getMatched());
        return preview.getMatched();
    }

    private String readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 以 image 类型发送图片到企业微信群机器人，返回 errmsg */
    private String sendWebhookImage(String webhook, byte[] png) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(png);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "image");
        Map<String, Object> image = new HashMap<>();
        image.put("base64", Base64.getEncoder().encodeToString(png));
        image.put("md5", hex.toString());
        body.put("image", image);
        byte[] payload = objectMapper.writeValueAsBytes(body);

        HttpURLConnection conn = (HttpURLConnection) new URL(webhook).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }
        int code = conn.getResponseCode();
        String resp;
        try (java.io.InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()) {
            resp = readAll(in);
        }
        conn.disconnect();
        Map<?, ?> json = objectMapper.readValue(resp, Map.class);
        Object errcode = json.get("errcode");
        Object errmsg = json.get("errmsg");
        if (errcode != null && "0".equals(errcode.toString())) return "ok";
        return String.valueOf(errmsg == null ? resp : errmsg);
    }

    private void recordHistory(NotifyConfig config, int matched, String result, String message, String error) {
        try {
            NotifyHistory h = new NotifyHistory();
            h.setConfigId(config.getId());
            h.setConfigName(config.getConfigName());
            h.setPushTime(LocalDateTime.now());
            h.setMatched(matched);
            h.setResult(result);
            if (message != null && message.length() > 2000) message = message.substring(0, 2000);
            h.setMessage(message);
            if (error != null && error.length() > 500) error = error.substring(0, 500);
            h.setError(error);
            historyMapper.insert(h);
        } catch (Exception e) {
            log.error("记录推送历史失败", e);
        }
    }

    /** 推送历史（最近 100 条） */
    public List<NotifyHistory> history() {
        return historyMapper.selectList(Wrappers.<NotifyHistory>lambdaQuery()
                .orderByDesc(NotifyHistory::getPushTime).last("LIMIT 100"));
    }
}
