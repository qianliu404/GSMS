package com.gsms.module.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {

    /** 按案件类型统计数量 */
    List<Map<String, Object>> countByType(@Param("allScope") boolean allScope,
                                          @Param("userId") String userId,
                                          @Param("deptIds") List<String> deptIds);

    /** 按状态统计数量 */
    List<Map<String, Object>> countByStatus(@Param("allScope") boolean allScope,
                                            @Param("userId") String userId,
                                            @Param("deptIds") List<String> deptIds);

    /** 近 N 个月建档趋势 */
    List<Map<String, Object>> countByMonth(@Param("allScope") boolean allScope,
                                           @Param("userId") String userId,
                                           @Param("deptIds") List<String> deptIds,
                                           @Param("months") int months);

    /** 工伤待遇金额合计 */
    BigDecimal sumInjuryAmount(@Param("allScope") boolean allScope,
                               @Param("userId") String userId,
                               @Param("deptIds") List<String> deptIds);

    /** 仲裁裁决/调解金额合计 */
    BigDecimal sumAwardAmount(@Param("allScope") boolean allScope,
                              @Param("userId") String userId,
                              @Param("deptIds") List<String> deptIds);

    /** 平均结案天数 */
    Double avgCloseDays(@Param("allScope") boolean allScope,
                        @Param("userId") String userId,
                        @Param("deptIds") List<String> deptIds);

    /** 期限节点统计：总数与超期数 */
    Map<String, Object> deadlineStat(@Param("allScope") boolean allScope,
                                      @Param("userId") String userId,
                                      @Param("deptIds") List<String> deptIds);

    /** 任务完成情况 */
    Map<String, Object> taskStat(@Param("allScope") boolean allScope,
                                 @Param("userId") String userId,
                                 @Param("deptIds") List<String> deptIds);

    /** 按案件类型 + 状态分组计数，供流程总览的节点分布使用 */
    List<Map<String, Object>> countByTypeAndStatus(@Param("allScope") boolean allScope,
                                                   @Param("userId") String userId,
                                                   @Param("deptIds") List<String> deptIds);

    /** 本年度已结案数（按 close_date 落在本自然年） */
    Integer countClosedThisYear(@Param("allScope") boolean allScope,
                                @Param("userId") String userId,
                                @Param("deptIds") List<String> deptIds);

    /** 未完成的期限节点，按截止日期升序，用于总览的待办与预警 */
    List<Map<String, Object>> urgentDeadlines(@Param("allScope") boolean allScope,
                                              @Param("userId") String userId,
                                              @Param("deptIds") List<String> deptIds,
                                              @Param("limit") int limit);

    /** 台账统计卡：案件数、待认定、待鉴定、待遇未结清，一次聚合完成 */
    Map<String, Object> ledgerSummary(@Param("allScope") boolean allScope,
                                      @Param("userId") String userId,
                                      @Param("deptIds") List<String> deptIds);
}
