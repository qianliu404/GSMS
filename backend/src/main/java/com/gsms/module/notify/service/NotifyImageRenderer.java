package com.gsms.module.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * 把推送内容渲染成 PNG 图片（表格样式）。
 * 企业微信 image 消息要求 base64（无换行）+ md5。
 *
 * 清晰度策略：以 20px 级字体直接渲染，避免小图被客户端放大后发虚；
 * 同时开启抗锯齿、分数度量与高质量渲染提示。
 *
 * 宽度策略：先按「表头 + 每列最宽单元格」实测像素宽度得到自然列宽，
 * 图片宽度 = 列宽之和，因此字段越多、内容越长，图片自动变宽而不是挤压截断。
 */
@Slf4j
@Component
public class NotifyImageRenderer {

    /** 表格最小宽度：字段少时保持版式舒展，不足则等比放大 */
    private static final int MIN_TABLE_W = 1072;
    /** 表格最大宽度：字段极多时的长图上限，超出才等比压缩 */
    private static final int MAX_TABLE_W = 2600;
    private static final int PAD = 40;
    private static final int CARD_PAD = 24;
    private static final int ROW_H = 50;
    private static final int HEAD_H = 54;
    /** 折行后的行间距 */
    private static final int LINE_H = 30;
    /** 多行单元格上下内边距 */
    private static final int ROW_PAD = 10;
    /** 单元格左右内边距 */
    private static final int CELL_PAD = 12;
    /** 单列宽度上下限 */
    private static final int MIN_COL = 100;
    private static final int MAX_COL = 360;
    /** 长文本列（案件名称、部门、班组）允许更宽 */
    private static final int MAX_COL_WIDE = 560;

    private static final Color BG = new Color(248, 250, 253);
    private static final Color CARD = Color.WHITE;
    private static final Color PRIMARY = new Color(30, 94, 255);
    private static final Color TEXT = new Color(28, 36, 52);
    private static final Color SUB = new Color(96, 108, 128);
    private static final Color LINE = new Color(226, 232, 240);
    private static final Color HEAD_BG = new Color(238, 243, 255);
    private static final Color ZEBRA = new Color(250, 251, 253);
    private static final Color WARN_FG = new Color(198, 86, 10);

    /** 字段 -> 表头文字（列顺序由用户勾选决定） */
    private static final Map<String, String> HEADERS = new LinkedHashMap<>();
    static {
        HEADERS.put("caseNo", "案号");
        HEADERS.put("caseName", "案件名称");
        HEADERS.put("caseType", "类型");
        HEADERS.put("nodeName", "当前节点");
        HEADERS.put("deadlineDate", "截止日期");
        HEADERS.put("remainDays", "剩余天数");
        HEADERS.put("handlerName", "办理人");
        HEADERS.put("employeeName", "当事人");
        HEADERS.put("deptName", "责任部门");
        HEADERS.put("plantArea", "厂区");
        HEADERS.put("workshopTeam", "车间班组");
        HEADERS.put("occurDate", "发生日期");
        HEADERS.put("deadlineTypeName", "期限类型");
        HEADERS.put("statusName", "期限状态");
    }

    private Font font(int size, boolean bold) {
        int style = bold ? Font.BOLD : Font.PLAIN;
        String[] candidates = {"Microsoft YaHei", "微软雅黑", "SimHei", "黑体", "SimSun", "宋体"};
        for (String name : candidates) {
            Font f = new Font(name, style, size);
            if (!"Dialog".equals(f.getName())) return f;
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    /** 渲染 PNG，返回字节数组（图片宽度随字段数与内容长度自适应） */
    public byte[] render(String title, List<Map<String, Object>> rows, List<String> fieldOrder) {
        Font fTitle = font(38, true);
        Font fSub = font(21, false);
        Font fHead = font(22, true);
        Font fBody = font(22, false);

        List<String> cols = normalizeCols(fieldOrder);

        // 离屏 Graphics 只用于测量文字像素宽，得到自适应列宽
        Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        probe.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        probe.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics fmHead = probe.getFontMetrics(fHead);
        FontMetrics fmBody = probe.getFontMetrics(fBody);
        int[] widths = autoWidths(cols, rows, fmHead, fmBody);
        probe.dispose();

        int tableWidth = 0;
        for (int w : widths) tableWidth += w;
        int width = tableWidth + PAD * 2 + CARD_PAD * 2;

        // 顶部：色条 8 + 标题 + 时间
        int cardTop = PAD + 8 + 54 + 38;
        int statH = 46;
        int[] rowHeights = measureRowHeights(cols, widths, rows, fmBody);
        int tableBodyH = 0;
        for (int h : rowHeights) tableBodyH += h;
        int cardH = rows.isEmpty() ? 140 : statH + HEAD_H + tableBodyH + 24;
        int height = Math.max(300, cardTop + cardH + PAD);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(BG);
        g.fillRect(0, 0, width, height);

        // 顶部色条
        g.setColor(PRIMARY);
        g.fillRect(0, 0, width, 8);

        // 标题
        g.setFont(fTitle);
        g.setColor(TEXT);
        g.drawString(title, PAD, PAD + 42);
        // 统计时间（不加规则行）
        g.setFont(fSub);
        g.setColor(SUB);
        g.drawString("统计时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                PAD, PAD + 8 + 54 + 10);

        // 卡片
        g.setColor(CARD);
        g.fillRoundRect(PAD, cardTop, width - PAD * 2, cardH, 12, 12);
        g.setColor(LINE);
        g.drawRoundRect(PAD, cardTop, width - PAD * 2, cardH, 12, 12);

        int left = PAD + CARD_PAD;
        int cy = cardTop + 18;

        // 统计行
        g.setFont(fHead);
        g.setColor(PRIMARY);
        g.drawString("共 " + rows.size() + " 件", left, cy + 22);
        cy += statH;

        if (rows.isEmpty()) {
            g.setFont(fBody);
            g.setColor(SUB);
            g.drawString("当前无符合条件的案件", left, cy + 30);
        } else {
            drawTable(g, cols, widths, rows, rowHeights, left, cy, fHead, fBody);
        }
        g.dispose();

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("渲染推送图片失败", e);
        }
    }

    /** 绘制表格：表头 + 斑马纹数据行（多行自适应行高）+ 网格线 */
    private void drawTable(Graphics2D g, List<String> cols, int[] widths,
                           List<Map<String, Object>> rows, int[] rowHeights,
                           int left, int top, Font fHead, Font fBody) {
        int totalW = 0;
        for (int w : widths) totalW += w;

        // 表头背景
        g.setColor(HEAD_BG);
        g.fillRect(left, top, totalW, HEAD_H);

        // 表头文字（单行，垂直居中）
        g.setFont(fHead);
        g.setColor(PRIMARY);
        FontMetrics fmHead = g.getFontMetrics();
        int x = left;
        for (int i = 0; i < cols.size(); i++) {
            String head = HEADERS.getOrDefault(cols.get(i), cols.get(i));
            int baseline = top + (HEAD_H - fmHead.getHeight()) / 2 + fmHead.getAscent();
            g.drawString(head, x + CELL_PAD, baseline);
            x += widths[i];
        }

        // 数据行：单元格内容超宽自动折行，行高取该行最多行数
        g.setFont(fBody);
        FontMetrics fm = g.getFontMetrics();
        List<Integer> lineYs = new ArrayList<>();
        lineYs.add(top);
        int y = top + HEAD_H;
        for (int r = 0; r < rows.size(); r++) {
            Map<String, Object> row = rows.get(r);
            int rowH = rowHeights[r];
            if (r % 2 == 1) {
                g.setColor(ZEBRA);
                g.fillRect(left, y, totalW, rowH);
            }
            // 先折行，取最大行数决定文字块的垂直居中位置
            List<List<String>> cellLines = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < cols.size(); i++) {
                List<String> ls = wrapText(valueOf(row, cols.get(i)), fm, widths[i] - CELL_PAD * 2);
                cellLines.add(ls);
                if (ls.size() > maxLines) maxLines = ls.size();
            }
            int blockH = maxLines * LINE_H;
            int textTop = y + (rowH - blockH) / 2;

            int cx = left;
            for (int i = 0; i < cols.size(); i++) {
                String col = cols.get(i);
                Long remain = col.equals("remainDays") ? asLong(row.get("remainDays")) : null;
                boolean warn = remain != null && remain <= 0;
                g.setColor(warn ? WARN_FG : TEXT);
                List<String> ls = cellLines.get(i);
                for (int k = 0; k < ls.size(); k++) {
                    int baseline = textTop + k * LINE_H + (LINE_H - fm.getHeight()) / 2 + fm.getAscent();
                    g.drawString(ls.get(k), cx + CELL_PAD, baseline);
                }
                cx += widths[i];
            }
            y += rowH;
            lineYs.add(y);
        }

        // 网格线
        g.setColor(LINE);
        g.drawLine(left, top, left + totalW, top);
        for (int ly : lineYs) {
            g.drawLine(left, ly, left + totalW, ly);
        }
        g.drawLine(left, y, left + totalW, y);
        x = left;
        for (int i = 0; i < widths.length; i++) {
            g.drawLine(x, top, x, y);
            x += widths[i];
        }
        g.drawLine(left + totalW, top, left + totalW, y);
    }

    /** 按像素宽度折行；中文按字断行，空值返回单个空行 */
    private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (cur.length() > 0 && fm.stringWidth(cur.toString() + ch) > maxWidth) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    /** 逐行计算行高（与 drawTable 的折行逻辑保持一致） */
    private int[] measureRowHeights(List<String> cols, int[] widths,
                                    List<Map<String, Object>> rows, FontMetrics fm) {
        int[] heights = new int[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            Map<String, Object> row = rows.get(r);
            int maxLines = 1;
            for (int i = 0; i < cols.size(); i++) {
                int lines = wrapText(valueOf(row, cols.get(i)), fm, widths[i] - CELL_PAD * 2).size();
                if (lines > maxLines) maxLines = lines;
            }
            heights[r] = Math.max(ROW_H, maxLines * LINE_H + ROW_PAD * 2);
        }
        return heights;
    }

    private List<String> normalizeCols(List<String> fieldOrder) {
        List<String> cols = new ArrayList<>();
        if (fieldOrder != null) {
            for (String f : fieldOrder) if (HEADERS.containsKey(f) && !cols.contains(f)) cols.add(f);
        }
        if (cols.isEmpty()) {
            cols.add("caseNo");
            cols.add("caseName");
            cols.add("nodeName");
            cols.add("deadlineDate");
            cols.add("remainDays");
        }
        return cols;
    }

    /**
     * 自适应列宽：每列取「表头宽」与「该列所有单元格宽」的较大值 + 内边距，
     * 再按单元格内边距限制在 [MIN_COL, 上限]；总和不足最小表宽则等比拉伸，
     * 超出最大表宽才等比压缩（此时图片已经很宽，压缩也不会挤成碎片）。
     */
    private int[] autoWidths(List<String> cols, List<Map<String, Object>> rows,
                             FontMetrics fmHead, FontMetrics fmBody) {
        int n = cols.size();
        int[] natural = new int[n];
        for (int i = 0; i < n; i++) {
            String code = cols.get(i);
            int w = fmHead.stringWidth(HEADERS.getOrDefault(code, code));
            for (Map<String, Object> r : rows) {
                w = Math.max(w, fmBody.stringWidth(valueOf(r, code)));
            }
            w += CELL_PAD * 2 + 8; // 左右内边距 + 一点余量，避免贴边
            int cap = isWideCol(code) ? MAX_COL_WIDE : MAX_COL;
            natural[i] = Math.max(MIN_COL, Math.min(w, cap));
        }

        int naturalTotal = 0;
        for (int w : natural) naturalTotal += w;

        int tableW;
        if (naturalTotal < MIN_TABLE_W) tableW = MIN_TABLE_W;
        else if (naturalTotal > MAX_TABLE_W) tableW = MAX_TABLE_W;
        else tableW = naturalTotal;

        if (tableW == naturalTotal) {
            return natural;
        }
        int[] out = new int[n];
        int used = 0;
        for (int i = 0; i < n; i++) {
            out[i] = (int) Math.floor((double) tableW * natural[i] / naturalTotal);
            used += out[i];
        }
        out[n - 1] += tableW - used;
        return out;
    }

    /** 长文本列允许更宽，避免案件名/部门被过度截断 */
    private boolean isWideCol(String code) {
        return "caseName".equals(code) || "deptName".equals(code) || "workshopTeam".equals(code)
                || "employeeName".equals(code) || "plantArea".equals(code);
    }

    private Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private String valueOf(Map<String, Object> row, String f) {
        switch (f) {
            case "caseType": {
                String t = s(row.get("caseType"));
                return "INJURY".equals(t) ? "工伤" : "ARBITRATION".equals(t) ? "劳动仲裁" : t;
            }
            case "nodeName": return s(row.get("nodeName"));
            case "deadlineDate": return s(row.get("deadlineDate"));
            case "remainDays": {
                Long d = asLong(row.get("remainDays"));
                if (d == null) return "—";
                if (d < 0) return "超期 " + (-d) + " 天";
                if (d == 0) return "今日到期";
                return d + " 天";
            }
            case "handlerName": return dash(row.get("handlerName"));
            case "deptName": return dash(row.get("deptName"));
            case "plantArea": return dash(row.get("plantArea"));
            case "workshopTeam": return dash(row.get("workshopTeam"));
            case "employeeName": return dash(row.get("employeeName"));
            case "occurDate": return dash(row.get("occurDate"));
            case "deadlineTypeName": return dash(row.get("deadlineTypeName"));
            case "statusName": return dash(row.get("statusName"));
            case "caseNo": return dash(row.get("caseNo"));
            case "caseName": return dash(row.get("caseName"));
            default: return "";
        }
    }

    private String dash(Object o) {
        String v = s(o);
        return v.isEmpty() ? "—" : v;
    }

    private String s(Object o) { return o == null ? "" : o.toString(); }

    /** 生成可直接用于 <img src> 的 data URL */
    public String toDataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
