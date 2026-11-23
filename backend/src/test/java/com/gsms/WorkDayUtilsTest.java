package com.gsms;

import com.gsms.common.WorkDayUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 节点截止日期的工作日推算：周末顺延、非数值时限不设截止日。
 */
class WorkDayUtilsTest {

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
