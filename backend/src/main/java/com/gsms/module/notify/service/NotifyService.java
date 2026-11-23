package com.gsms.module.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

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
    }
    public static final String DEFAULT_FIELDS = "caseNo,caseName,nodeName,deadlineDate,remainDays,handlerName";

    private final NotifyConfigMapper configMapper;
    private final NotifyHistoryMapper historyMapper;
    private final NotifyQueryMapper queryMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gsms.notify.webhook:}")
    private String defaultWebhook;

    public NotifyService(NotifyConfigMapper configMapper, NotifyHistoryMapper historyMapper,
                         NotifyQueryMapper queryMapper) {
        this.configMapper = configMapper;
        this.historyMapper = historyMapper;
        this.queryMapper = queryMapper;
    }

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
        if (c.getAheadDays() == null || c.getAheadDays() < 1 || c.getAheadDays() > 365) {
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
        if (c.getIncludeOverdue() == null) c.setIncludeOverdue(1);
        if (c.getEnabled() == null) c.setEnabled(1);
    }

    /* ------------------------- 预览 ------------------------- */

    /** 按临时参数预览（前端未保存时也能预览） */
    public NotifyPreviewVO preview(NotifyConfig config) {
        if (config.getAheadDays() == null) config.setAheadDays(7);
        if (config.getIncludeOverdue() == null) config.setIncludeOverdue(1);
        if (config.getFields() == null || config.getFields().trim().isEmpty()) config.setFields(DEFAULT_FIELDS);
        List<NotifyPreviewVO.FieldRow> rows = queryMatches(config.getAheadDays(), config.getIncludeOverdue() == 1);
        return buildPreview(config, rows);
    }

    /** 按已保存配置 ID 预览 */
    public NotifyPreviewVO previewById(Long id) {
        return preview(get(id));
    }

    private List<NotifyPreviewVO.FieldRow> queryMatches(int aheadDays, boolean includeOverdue) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(aheadDays);
        List<Map<String, Object>> raw = queryMapper.selectMatches(today, limit, includeOverdue);
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
            rows.add(r);
        }
        return rows;
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }

    private NotifyPreviewVO buildPreview(NotifyConfig config, List<NotifyPreviewVO.FieldRow> rows) {
        NotifyPreviewVO vo = new NotifyPreviewVO();
        vo.setRows(rows);
        vo.setMatched(rows.size());
        List<NotifyPreviewVO.FieldMeta> fields = new ArrayList<>();
        Set<String> selected = parseFields(config.getFields());
        for (Map.Entry<String, String> e : FIELD_LABELS.entrySet()) {
            fields.add(new NotifyPreviewVO.FieldMeta(e.getKey(), e.getValue(), selected.contains(e.getKey())));
        }
        vo.setAvailableFields(fields);
        vo.setContent(buildContent(config, rows, selected));
        return vo;
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

    /** 生成 Markdown 正文——预览与推送共用 */
    private String buildContent(NotifyConfig config, List<NotifyPreviewVO.FieldRow> rows, Set<String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(config.getConfigName() == null ? "案件期限提醒" : config.getConfigName()).append("\n");
        sb.append("> 统计时间：").append(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
        sb.append("> 规则：未结案 + 当前节点未完成期限 + 距截止不足 ").append(config.getAheadDays()).append(" 天")
          .append(config.getIncludeOverdue() != null && config.getIncludeOverdue() == 1 ? "（含已超期）" : "").append("\n\n");
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
        String result;
        try {
            result = sendWebhook(webhook, preview.getContent());
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

    /** 调用企业微信群机器人 webhook，返回 errmsg；异常抛出由上层记录 */
    private String sendWebhook(String webhook, String content) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        Map<String, Object> md = new HashMap<>();
        md.put("content", content);
        body.put("markdown", md);
        byte[] payload = objectMapper.writeValueAsBytes(body);

        HttpURLConnection conn = (HttpURLConnection) new URL(webhook).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
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
