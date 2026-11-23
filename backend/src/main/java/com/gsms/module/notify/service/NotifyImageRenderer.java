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
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * 把推送消息渲染成 PNG 图片：企业微信 image 消息要求 base64（无换行）+ md5。
 * 字体优先微软雅黑，找不到时回退到系统默认中文字体。
 */
@Slf4j
@Component
public class NotifyImageRenderer {

    private static final int WIDTH = 720;
    private static final int PAD = 32;
    private static final Color BG = new Color(248, 250, 253);
    private static final Color CARD = Color.WHITE;
    private static final Color PRIMARY = new Color(30, 94, 255);
    private static final Color TEXT = new Color(33, 43, 60);
    private static final Color SUB = new Color(96, 108, 128);
    private static final Color LINE = new Color(228, 233, 241);
    private static final Color WARN_BG = new Color(255, 243, 229);
    private static final Color WARN_FG = new Color(199, 92, 12);

    private Font font(int size) {
        String[] candidates = {"Microsoft YaHei", "微软雅黑", "SimHei", "黑体", "SimSun", "宋体"};
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, size);
            if (!"Dialog".equals(f.getName())) return f;
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    /** 渲染 PNG，返回字节数组 */
    public byte[] render(String title, String rule, List<Map<String, Object>> rows, List<String> fieldOrder) {
        Font fTitle = font(24);
        Font fSub = font(14);
        Font fHead = font(16);
        Font fBody = font(15);
        Font fSmall = font(13);

        // 先测量高度
        int y = PAD;
        y += 42; // 标题
        y += 26; // 时间
        y += 30; // 规则
        y += 18;
        if (rows.isEmpty()) {
            y += 60;
        } else {
            y += 26; // 统计
            for (Map<String, Object> row : rows) {
                y += 30; // 案号+案名
                String detail = detailLine(row, fieldOrder);
                y += wrapLines(detail, fBody, WIDTH - PAD * 2 - 20) * 22;
                y += 12;
            }
        }
        y += PAD;
        int height = Math.max(220, y);

        BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, WIDTH, height);

        // 顶部色条
        g.setColor(PRIMARY);
        g.fillRect(0, 0, WIDTH, 6);

        int cy = PAD + 4;
        g.setFont(fTitle);
        g.setColor(TEXT);
        g.drawString(title, PAD, cy + 22);
        cy += 42;
        g.setFont(fSub);
        g.setColor(SUB);
        g.drawString("统计时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), PAD, cy);
        cy += 24;
        if (rule != null && !rule.isEmpty()) {
            g.setColor(SUB);
            g.drawString(rule, PAD, cy);
            cy += 26;
        }
        cy += 10;

        // 卡片区
        int cardTop = cy;
        int cardBottom = height - PAD;
        g.setColor(CARD);
        g.fillRoundRect(PAD - 8, cardTop, WIDTH - (PAD - 8) * 2, cardBottom - cardTop, 10, 10);
        g.setColor(LINE);
        g.drawRoundRect(PAD - 8, cardTop, WIDTH - (PAD - 8) * 2, cardBottom - cardTop, 10, 10);

        cy += 26;
        if (rows.isEmpty()) {
            g.setFont(fHead);
            g.setColor(SUB);
            g.drawString("当前无符合条件的案件", PAD, cy + 20);
        } else {
            g.setFont(fHead);
            g.setColor(PRIMARY);
            g.drawString("共 " + rows.size() + " 件", PAD, cy);
            cy += 26;
            int idx = 0;
            for (Map<String, Object> row : rows) {
                idx++;
                g.setFont(fHead);
                g.setColor(TEXT);
                g.drawString(idx + ". " + s(row.get("caseNo")) + "  " + s(row.get("caseName")), PAD, cy);
                cy += 24;
                String detail = detailLine(row, fieldOrder);
                g.setFont(fBody);
                g.setColor(SUB);
                for (String line : wrap(detail, fBody, WIDTH - PAD * 2 - 20)) {
                    g.drawString(line, PAD + 12, cy);
                    cy += 22;
                }
                cy += 12;
                if (cy < cardBottom - 20) {
                    g.setColor(LINE);
                    g.drawLine(PAD, cy - 6, WIDTH - PAD, cy - 6);
                }
            }
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

    /** 详情行：按字段顺序拼接（案号/案名已单独成行，此处跳过） */
    private String detailLine(Map<String, Object> row, List<String> fieldOrder) {
        List<String> parts = new ArrayList<>();
        for (String f : fieldOrder) {
            if ("caseNo".equals(f) || "caseName".equals(f)) continue;
            String v = valueOf(row, f);
            if (v != null && !v.isEmpty()) parts.add(label(f) + "：" + v);
        }
        return String.join("   ", parts);
    }

    private String valueOf(Map<String, Object> row, String f) {
        switch (f) {
            case "caseType":
                String t = s(row.get("caseType"));
                return "INJURY".equals(t) ? "工伤" : "ARBITRATION".equals(t) ? "劳动仲裁" : t;
            case "nodeName": return s(row.get("nodeName"));
            case "deadlineDate": return s(row.get("deadlineDate"));
            case "remainDays":
                Object rd = row.get("remainDays");
                if (rd == null) return "";
                long d = ((Number) rd).longValue();
                if (d < 0) return "已超期 " + (-d) + " 天";
                if (d == 0) return "今日到期";
                return "剩余 " + d + " 天";
            case "handlerName": return s(row.get("handlerName"));
            case "deptName": return s(row.get("deptName"));
            case "plantArea": return s(row.get("plantArea"));
            case "workshopTeam": return s(row.get("workshopTeam"));
            case "employeeName": return s(row.get("employeeName"));
            case "occurDate": return s(row.get("occurDate"));
            case "deadlineTypeName": return s(row.get("deadlineTypeName"));
            case "statusName": return s(row.get("statusName"));
            default: return "";
        }
    }

    private String label(String f) {
        switch (f) {
            case "caseType": return "类型";
            case "nodeName": return "节点";
            case "deadlineDate": return "截止";
            case "remainDays": return "期限";
            case "handlerName": return "办理人";
            case "deptName": return "部门";
            case "plantArea": return "厂区";
            case "workshopTeam": return "班组";
            case "employeeName": return "当事人";
            case "occurDate": return "发生日期";
            case "deadlineTypeName": return "期限类型";
            case "statusName": return "状态";
            default: return f;
        }
    }

    private String s(Object o) { return o == null ? "" : o.toString(); }

    /** 按像素宽度折行（中文按整字处理） */
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

    private int wrapLines(String text, Font font, int maxWidth) {
        return Math.max(1, wrap(text, font, maxWidth).size());
    }

    /** 生成可直接用于 <img src> 的 data URL */
    public String toDataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
