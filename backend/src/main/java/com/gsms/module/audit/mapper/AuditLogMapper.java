package com.gsms.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsms.module.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /** 统计超过保留期的审计日志条数 */
    @Select("SELECT COUNT(*) FROM audit_log WHERE operate_time < #{cutoff}")
    long countExpired(@Param("cutoff") LocalDateTime cutoff);

    /** 分批删除，避免一次大事务锁表；operate_time 已有索引 */
    @Delete("DELETE FROM audit_log WHERE operate_time < #{cutoff} LIMIT #{limit}")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
