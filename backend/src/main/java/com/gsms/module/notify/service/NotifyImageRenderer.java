package com.gsms.module.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
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
 * 清晰度策略：直接以 1200px 宽、20px 级字体渲染，避免小图被客户端放大后发虚；
 * 同时开启抗锯齿、分数度量与高质量渲染提示。
 */
@Slf4j
@Component
public class NotifyImageRenderer {

    private static final int WIDTH = 1200;
    private static final int PAD = 40;
    private static final int CARD_PAD = 24;
    private static final int ROW_H = 50;
    private static final int HEAD_H = 54;

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

    /** 渲染 PNG，返回字节数组 */
    public byte[] render(String title, List<Map<String, Object>> rows, List<String> fieldOrder) {
        Font fTitle = font(36, true);
        Font fSub = font(20, false);
        Font fHead = font(21, true);
        Font fBody = font(21, false);

        List<String> cols = normalizeCols(fieldOrder);
        int tableWidth = WIDTH - PAD * 2 - CARD_PAD * 2;
        int[] widths = computeWidths(cols, tableWidth);

        // 顶部：色条 8 + 标题 + 时间
        int cardTop = PAD + 8 + 52 + 36;
        int statH = 46;
        int tableH = HEAD_H + rows.size() * ROW_H;
        int cardH = statH + tableH + 24;
        int height = Math.max(300, cardTop + cardH + PAD);

        BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(BG);
        g.fillRect(0, 0, WIDTH, height);

        // 顶部色条
        g.setColor(PRIMARY);
        g.fillRect(0, 0, WIDTH, 8);

        // 标题
        g.setFont(fTitle);
        g.setColor(TEXT);
        g.drawString(title, PAD, PAD + 40);
        // 统计时间（不加规则行）
        g.setFont(fSub);
        g.setColor(SUB);
        g.drawString("统计时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                PAD, PAD + 8 + 52 + 8);

        // 卡片
        g.setColor(CARD);
        g.fillRoundRect(PAD, cardTop, WIDTH - PAD * 2, cardH, 12, 12);
        g.setColor(LINE);
        g.drawRoundRect(PAD, cardTop, WIDTH - PAD * 2, cardH, 12, 12);

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
            drawTable(g, cols, widths, rows, left, cy, fHead, fBody);
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

    /** 绘制表格：表头 + 斑马纹数据行 + 网格线 */
    private void drawTable(Graphics2D g, List<String> cols, int[] widths,
                           List<Map<String, Object>> rows, int left, int top,
                           Font fHead, Font fBody) {
        int totalW = 0;
        for (int w : widths) totalW += w;

        // 表头背景
        g.setColor(HEAD_BG);
        g.fillRect(left, top, totalW, HEAD_H);

        // 表头文字
        g.setFont(fHead);
        g.setColor(PRIMARY);
        int x = left;
        for (int i = 0; i < cols.size(); i++) {
            String head = HEADERS.getOrDefault(cols.get(i), cols.get(i));
            drawCellText(g, head, x + 10, top + HEAD_H, widths[i] - 16, true);
            x += widths[i];
        }

        // 数据行
        int y = top + HEAD_H;
        g.setFont(fBody);
        for (int r = 0; r < rows.size(); r++) {
            Map<String, Object> row = rows.get(r);
            if (r % 2 == 1) {
                g.setColor(ZEBRA);
                g.fillRect(left, y, totalW, ROW_H);
            }
            int cx = left;
            for (int i = 0; i < cols.size(); i++) {
                String col = cols.get(i);
                String v = valueOf(row, col);
                Long remain = col.equals("remainDays") ? asLong(row.get("remainDays")) : null;
                boolean warn = remain != null && remain <= 0;
                g.setColor(warn ? WARN_FG : TEXT);
                drawCellText(g, v, cx + 10, y + ROW_H, widths[i] - 16, false);
                cx += widths[i];
            }
            y += ROW_H;
        }

        // 网格线
        g.setColor(LINE);
        g.drawLine(left, top, left + totalW, top);
        g.drawLine(left, y, left + totalW, y);
        for (int r = 0; r <= rows.size(); r++) {
            int ly = top + HEAD_H + r * ROW_H;
            g.drawLine(left, ly, left + totalW, ly);
        }
        x = left;
        for (int i = 0; i < widths.length; i++) {
            g.drawLine(x, top, x, y);
            x += widths[i];
        }
        g.drawLine(left + totalW, top, left + totalW, y);
    }

    /** 单元格文字：垂直居中，超宽截断加省略号 */
    private void drawCellText(Graphics2D g, String text, int x, int rowBottom, int maxWidth, boolean header) {
        FontMetrics fm = g.getFontMetrics();
        String draw = text == null ? "" : text;
        if (fm.stringWidth(draw) > maxWidth) {
            StringBuilder sb = new StringBuilder();
            for (char ch : draw.toCharArray()) {
                if (fm.stringWidth(sb.toString() + ch + "…") > maxWidth) break;
                sb.append(ch);
            }
            draw = sb + "…";
        }
        int baseline = rowBottom - (ROW_H - fm.getAscent()) / 2 - 6;
        g.drawString(draw, x, baseline);
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

    /** 列宽：案名权重最高，案号次之，其余均分 */
    private int[] computeWidths(List<String> cols, int total) {
        double[] weight = new double[cols.size()];
        double sum = 0;
        for (int i = 0; i < cols.size(); i++) {
            String c = cols.get(i);
            double w = 1.0;
            if ("caseName".equals(c)) w = 2.6;
            else if ("caseNo".equals(c)) w = 1.6;
            else if ("workshopTeam".equals(c) || "deptName".equals(c)) w = 1.3;
            else if ("remainDays".equals(c) || "deadlineDate".equals(c) || "occurDate".equals(c)) w = 1.2;
            weight[i] = w;
            sum += w;
        }
        int[] widths = new int[cols.size()];
        int used = 0;
        for (int i = 0; i < cols.size(); i++) {
            widths[i] = (int) Math.floor(total * weight[i] / sum);
            used += widths[i];
        }
        widths[widths.length - 1] += total - used;
        return widths;
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

    /** 按像素宽度折行（保留给未来多行单元格使用） */
    private List<String> wrap(String text, Font font, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        FontRenderContext frc = new FontRenderContext(null, true, true);
        StringBuilder cur = new StringBuilder();
        for (char ch : text.toCharArray()) {
            cur.append(ch);
            Rectangle2D b = font.getStringBounds(cur.toString(), frc);
            if (b.getWidth() > maxWidth) {
                cur.deleteCharAt(cur.length() - 1);
                if (cur.length() > 0) lines.add(cur.toString());
                cur = new StringBuilder();
                cur.append(ch);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    /** 生成可直接用于 <img src> 的 data URL */
    public String toDataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
