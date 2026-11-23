package com.gsms.common;

import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点截止日期计算：时限天数按工作日推进。
 *
 * 说明：本机未接入法定节假日日历，工作日口径为「周一至周五」，
 * 周六、周日顺延。若后续需要按国务院节假日安排调整，在此替换 {@link #isWorkDay} 即可。
 */
public final class WorkDayUtils {

    private static final Pattern DAYS = Pattern.compile("(\\d+)");

    private WorkDayUtils() {
    }

    /** 是否工作日（周一~周五） */
    public static boolean isWorkDay(LocalDate date) {
        DayOfWeek week = date.getDayOfWeek();
        return week != DayOfWeek.SATURDAY && week != DayOfWeek.SUNDAY;
    }

    /** 从 start 起顺延 days 个工作日（不含 start 当天） */
    public static LocalDate plusWorkingDays(LocalDate start, int days) {
        LocalDate date = start;
        int left = days;
        while (left > 0) {
            date = date.plusDays(1);
            if (isWorkDay(date)) {
                left--;
            }
        }
        return date;
    }

    /**
     * 从「10 天」「3天」这类时限文案中取天数。
     * 「视情况」「—」等非数值时限返回 null，表示该节点不设截止日期。
     */
    public static Integer parseDays(String timeLimit) {
        if (!StringUtils.hasText(timeLimit)) {
            return null;
        }
        Matcher matcher = DAYS.matcher(timeLimit);
        if (!matcher.find()) {
            return null;
        }
        int days = Integer.parseInt(matcher.group(1));
        return days > 0 ? days : null;
    }
}
