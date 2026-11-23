package com.gsms.module.recycle.mapper;

import com.gsms.module.recycle.dto.RecycleItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 回收站数据访问：全部使用原生 SQL，绕过 @TableLogic 的 deleted=0 过滤。
 * 永久删除按表逐条清理，保证不留孤儿数据。
 */
public interface RecycleBinMapper {

    List<RecycleItemVO> pageDeletedCases(@Param("keyword") String keyword,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    long countDeletedCases(@Param("keyword") String keyword);

    List<RecycleItemVO> pageDeletedUsers(@Param("keyword") String keyword,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    long countDeletedUsers(@Param("keyword") String keyword);

    int restoreCases(@Param("ids") List<Long> ids);

    int restoreUsers(@Param("ids") List<Long> ids);

    // ---------- 永久删除：案件及其关联数据 ----------
    int purgeCaseInjuryExt(@Param("ids") List<Long> ids);

    int purgeCaseArbitrationExt(@Param("ids") List<Long> ids);

    int purgeCaseMember(@Param("ids") List<Long> ids);

    int purgeCaseStatusLog(@Param("ids") List<Long> ids);

    int purgeCaseRelation(@Param("ids") List<Long> ids);

    int purgeCaseDocument(@Param("ids") List<Long> ids);

    int purgeDeadlineReminder(@Param("ids") List<Long> ids);

    int purgeWfInstance(@Param("ids") List<Long> ids);

    int purgeTask(@Param("ids") List<Long> ids);

    int purgeCountersign(@Param("ids") List<Long> ids);

    int purgeNotification(@Param("ids") List<Long> ids);

    int purgeCases(@Param("ids") List<Long> ids);

    // ---------- 永久删除：用户 ----------
    int purgeUserRoleByUserIds(@Param("ids") List<Long> ids);

    int purgeUsers(@Param("ids") List<Long> ids);

    /** 永久删除前取出案件文档的物理路径，用于同步清理磁盘文件 */
    List<String> selectDocumentPaths(@Param("ids") List<Long> ids);

    /** 永久删除前取出案件编号，写入审计 */
    List<String> selectCaseNos(@Param("ids") List<Long> ids);

    /** 永久删除前取出登录名，写入审计 */
    List<String> selectUserIds(@Param("ids") List<Long> ids);
}
