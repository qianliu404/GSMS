package com.gsms.module.report.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工伤事故人员信息统计表行（对上级安监/人社部门报送的固定格式）。
 * 列顺序与报表模板严格一致，避免导出后人工调列。
 */
@Data
public class InjuryStatRow {

    /** 序号 */
    private int sortNo;
    /** 厂区 */
    private String plantArea;
    /** 车间班组 */
    private String workshopTeam;
    /** 姓名 */
    private String employeeName;
    /** 受伤原因及日期 */
    private String injuryDesc;
    /** 小班长 */
    private String leaderL1;
    /** 大班长 */
    private String leaderL2;
    /** 工伤认定情况 */
    private String recognizeName;
    /** 劳动能力鉴定结论 */
    private String appraisalLevel;
    /** 一次性伤残金申领 */
    private String subsidyDisability;
    /** 一次性医疗金申领 */
    private String subsidyMedical;
    /** 一次性就业金申领 */
    private String subsidyEmployment;

    /** 统计表固定 12 列，按模板顺序输出 */
    public List<Object> asCells() {
        List<Object> cells = new ArrayList<>(12);
        cells.add(sortNo);
        cells.add(plantArea);
        cells.add(workshopTeam);
        cells.add(employeeName);
        cells.add(injuryDesc);
        cells.add(leaderL1);
        cells.add(leaderL2);
        cells.add(recognizeName);
        cells.add(appraisalLevel);
        cells.add(subsidyDisability);
        cells.add(subsidyMedical);
        cells.add(subsidyEmployment);
        return cells;
    }

    /** 表头（与统计表模板一致） */
    public static List<String> headers() {
        List<String> head = new ArrayList<>(12);
        head.add("序号");
        head.add("厂区");
        head.add("车间班组");
        head.add("姓名");
        head.add("受伤原因及日期");
        head.add("小班长");
        head.add("大班长");
        head.add("工伤认定情况");
        head.add("劳动能力鉴定结论");
        head.add("一次性伤残金申领");
        head.add("一次性医疗金申领");
        head.add("一次性就业金申领");
        return head;
    }
}
