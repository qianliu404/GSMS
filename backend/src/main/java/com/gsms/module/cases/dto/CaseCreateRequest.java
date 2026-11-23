package com.gsms.module.cases.dto;

import lombok.Data;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

@Data
public class CaseCreateRequest {

    @NotBlank(message = "案件类型不能为空")
    @Pattern(regexp = "INJURY|ARBITRATION|RELATED", message = "案件类型只能是 INJURY/ARBITRATION/RELATED")
    private String caseType;

    /** 案号：立案时可自定义；留空则由系统按「GY/ZS/GL + 年月 + 4 位流水」自动生成 */
    @Size(max = 32, message = "案号不能超过 32 个字符")
    private String caseNo;

    /** 关联案件案号：选填，建档后自动与已有案件建立争议关联 */
    private String relatedCaseNo;

    /** 关联说明：随建档写入关联关系，留空用默认文案 */
    @Size(max = 500, message = "关联说明不能超过 500 字")
    private String relationDesc;

    @NotBlank(message = "案件名称不能为空")
    @Size(max = 200, message = "案件名称不能超过 200 字")
    private String caseName;

    /** 员工工号，选填（表单已移除该字段） */
    @Size(max = 32, message = "员工工号不能超过 32 字")
    private String employeeId;

    @NotBlank(message = "员工姓名不能为空")
    private String employeeName;

    /** 身份证号，明文传入，服务端加密存储 */
    private String idCardNo;

    /** 部门ID，选填；留空时由承办人（或建档人）所属部门自动带出 */
    private String departmentId;

    private String departmentName;

    /** 厂区 */
    @Size(max = 50, message = "厂区不能超过 50 字")
    private String plantArea;

    /** 车间班组 */
    @Size(max = 100, message = "车间班组不能超过 100 字")
    private String workshopTeam;

    /** 小班长 / 直属主管 */
    @Size(max = 50, message = "小班长不能超过 50 字")
    private String leaderL1;

    /** 大班长 / 上级主管 */
    @Size(max = 50, message = "大班长不能超过 50 字")
    private String leaderL2;

    @NotNull(message = "发生日期不能为空")
    @PastOrPresent(message = "发生日期不能晚于今天")
    private LocalDate occurDate;

    /** 案件描述，选填 */
    @Size(max = 5000, message = "案件描述不能超过 5000 字")
    private String description;

    /** 案件负责人，默认取承办人 */
    private String ownerId;

    @NotBlank(message = "承办人不能为空")
    private String handlerId;

    /** 协办人 */
    private List<String> coHandlerIds;

    /** 会签人 */
    private List<String> countersignerIds;

    /** 阅知人 */
    private List<String> readerIds;

    /** true 仅保存草稿，false 直接提交立案 */
    private boolean submit;

    private InjuryExtDTO injury;

    private ArbitrationExtDTO arbitration;
}
