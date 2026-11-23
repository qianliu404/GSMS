package com.gsms.module.cases.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsms.module.cases.entity.CaseMain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CaseMainMapper extends BaseMapper<CaseMain> {

    /** 某用户作为成员参与的案件ID（数据权限 SELF 范围使用） */
    @Select("SELECT DISTINCT case_id FROM case_member WHERE user_id = #{userId}")
    List<Long> selectMemberCaseIds(@Param("userId") String userId);

    /**
     * 生成案件编号时取该前缀下已有的最大流水号（含已删除案件）。
     * 不能用 COUNT：回收站永久删除会造成编号断层，COUNT+1 会撞 uk_case_no 唯一键。
     */
    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(case_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) " +
            "FROM case_main WHERE case_no LIKE CONCAT(#{prefix}, '%')")
    int maxSeqByPrefix(@Param("prefix") String prefix);

    /** 案号是否已被占用：含回收站里的案件，uk_case_no 唯一键是全局的 */
    @Select("SELECT COUNT(1) FROM case_main WHERE case_no = #{caseNo}")
    int countByCaseNo(@Param("caseNo") String caseNo);
}
