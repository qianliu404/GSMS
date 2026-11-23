package com.gsms.config;

import com.gsms.common.WorkDayUtils;
import org.springframework.context.annotation.Configuration;

/** 启动时把配置的法定节假日/调休日注入工作日计算器 */
@Configuration
public class WorkDayCalendarConfig {

    public WorkDayCalendarConfig(GsmsProperties properties) {
        WorkDayUtils.configure(properties.getCalendar().getHolidays(),
                properties.getCalendar().getMakeupWorkdays());
    }
}
