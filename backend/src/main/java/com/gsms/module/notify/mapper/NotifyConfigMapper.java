package com.gsms.module.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsms.module.notify.entity.NotifyConfig;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface NotifyConfigMapper extends BaseMapper<NotifyConfig> {

    /** 查询所有启用的配置（定时任务用） */
    @Select("SELECT * FROM notify_config WHERE enabled = 1 ORDER BY id")
    List<NotifyConfig> selectEnabled();
}
