package com.gsms.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReportDTO {

    @Data
    public static class ChartItem {
        private String name;
        private long value;

        public ChartItem() {
        }

        public ChartItem(String name, long value) {
            this.name = name;
            this.value = value;
        }
    }

    @Data
    public static class CaseSummary {
        private long total;
        private long openCount;
        private long closedCount;
        private long archivedCount;
        private List<ChartItem> byType = new ArrayList<>();
        private List<ChartItem> byStatus = new ArrayList<>();
        private List<ChartItem> monthly = new ArrayList<>();
    }

    @Data
    public static class Efficiency {
        private Double avgCloseDays;
        private long taskTotal;
        private long taskDone;
        private long taskOverdue;
        private double taskDoneRate;
    }

    @Data
    public static class Amount {
        private BigDecimal injuryTotal = BigDecimal.ZERO;
        private BigDecimal arbitrationTotal = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;
        /** 无权限查看金额时为 true，金额字段返回 0 */
        private boolean masked;
    }

    @Data
    public static class Overdue {
        private long deadlineTotal;
        private long deadlineOverdue;
        private long deadlineDone;
        private double overdueRate;
    }
}
