package com.gsms.common;

import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点截止日期计算：时限天数按工作日推进。
 *
 * 工作日口径：周一至周五为工作日；国务院公布的法定节假日（含调休放假）不计入，
 * 调休上班的周末计为工作日。节假日与调休日期由 {@link #configure} 在启动时从配置注入，
 * 可用环境变量 GSMS_HOLIDAYS / GSMS_MAKEUP_WORKDAYS 覆盖，无需改代码即可维护下一年安排。
 */
public final class WorkDayUtils {

    private static final Pattern DAYS = Pattern.compile("(\\d+)");
    private static final Set<LocalDate> HOLIDAYS = ConcurrentHashMap.newKeySet();
    private static final Set<LocalDate> MAKEUP_WORKDAYS = ConcurrentHashMap.newKeySet();

    private WorkDayUtils() {
    }

    /** 启动时注入节假日与调休工作日（yyyy-MM-dd，逗号分隔） */
    public static void configure(Collection<String> holidays, Collection<String> makeupWorkdays) {
        HOLIDAYS.clear();
        MAKEUP_WORKDAYS.clear();
        if (holidays != null) {
            holidays.stream().filter(StringUtils::hasText)
                    .forEach(v -> HOLIDAYS.add(LocalDate.parse(v.trim())));
        }
        if (makeupWorkdays != null) {
            makeupWorkdays.stream().filter(StringUtils::hasText)
                    .forEach(v -> MAKEUP_WORKDAYS.add(LocalDate.parse(v.trim())));
        }
    }

    /** 是否工作日：调休上班优先，其次法定节假日，最后按周一至周五判断 */
    public static boolean isWorkDay(LocalDate date) {
        if (MAKEUP_WORKDAYS.contains(date)) {
            return true;
        }
        if (HOLIDAYS.contains(date)) {
            return false;
        }
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

    /** 从「10 天」「3天」类时限文案中取天数；非数值时限返回 null */
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
