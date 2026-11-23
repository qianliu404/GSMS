package com.gsms;

import com.gsms.common.WorkDayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 节点截止日期的工作日推算：周末顺延、非数值时限不设截止日。
 */
class WorkDayUtilsTest {

    @BeforeEach
    void setUpHolidays() {
        // 国务院办公厅 2026 年安排节选：国庆 10-01~10-07 放假，10-10（周六）调休上班；春节 2-14 调休上班
        WorkDayUtils.configure(
                Arrays.asList("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04",
                        "2026-10-05", "2026-10-06", "2026-10-07"),
                Arrays.asList("2026-10-10", "2026-02-14"));
    }

    @Test
    void nationalDayHolidaysAreNotWorkingDays() {
        assertEquals(LocalDate.of(2026, 10, 8),
                WorkDayUtils.plusWorkingDays(LocalDate.of(2026, 9, 30), 1));
    }

    @Test
    void makeupWeekendIsWorkingDay() {
        assertEquals(LocalDate.of(2026, 10, 10),
                WorkDayUtils.plusWorkingDays(LocalDate.of(2026, 10, 9), 1));
        assertEquals(LocalDate.of(2026, 2, 14),
                WorkDayUtils.plusWorkingDays(LocalDate.of(2026, 2, 13), 1));
    }

    @Test
    void plusWorkingDaysSkipsWeekend() {
        // 2026-11-20 是周五，+3 个工作日 = 11-23(一)、11-24(二)、11-25(三)
        assertEquals(LocalDate.of(2026, 11, 25),
                WorkDayUtils.plusWorkingDays(LocalDate.of(2026, 11, 20), 3));
    }

    @Test
    void plusOneWorkingDayFromFridayLandsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 23),
                WorkDayUtils.plusWorkingDays(LocalDate.of(2026, 11, 20), 1));
    }

    @Test
    void startOnWeekendStillMovesForward() {
        // 2026-11-21 是周六，+1 个工作日 = 11-23（周一）
        assertEquals(LocalDate.of(2026, 11, 23),
                WorkDayUtils.plusWorkingDays(LocalDate.of(2026, 11, 21), 1));
    }

    @Test
    void plusFiveWorkingDaysCrossesWeekend() {
        // 周五 +5：11-23、24、25、26、27
        assertEquals(LocalDate.of(2026, 11, 27),
                WorkDayUtils.plusWorkingDays(LocalDate.of(2026, 11, 20), 5));
    }

    @Test
    void parseDaysReadsNumberFromText() {
        assertEquals(Integer.valueOf(10), WorkDayUtils.parseDays("10 天"));
        assertEquals(Integer.valueOf(3), WorkDayUtils.parseDays("3天"));
        assertNull(WorkDayUtils.parseDays("视情况"));
        assertNull(WorkDayUtils.parseDays("—"));
        assertNull(WorkDayUtils.parseDays(null));
    }
}
