package com.example.platform.task;

import com.example.platform.service.AlarmEvaluateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * 类功能:停报超窗确认恢复任务——设备恢复正常值后停止上报（或上报周期>静默窗口）时，
 *   评估链的恢复分支不再被触发，活动 METRIC 告警会悬挂；本任务定时扫描并做"超窗确认恢复"：
 *   最新采样已位于安全区、且距今超过 alarm.quiet-window-sec → 恢复。
 *   越限/死区状态不停报恢复——数据中断由 OFFLINE/网关离线链路表达，不由恢复链路兜。
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmQuietWindowRecoveryTask {

    private final AlarmEvaluateService alarmEvaluateService;

    /*
     * 方法功能:定时执行停报超窗确认恢复
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.alarm-quiet-recovery-cron:0 40/5 * * * ?}")
    public void recoverStaleConfirmed() {
        int recovered = alarmEvaluateService.recoverStaleConfirmedAlarms();
        if (recovered > 0) {
            log.info("停报超窗确认恢复完成 recovered={}", recovered);
        }
    }
}
