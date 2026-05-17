package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.Alert;
import com.example.platform.mapper.AlertMapper;
import com.example.platform.service.AlertService;
import com.example.platform.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {
    DeviceService deviceService;

    public AlertServiceImpl(DeviceService deviceService){
        this.deviceService = deviceService;
    }
    /*
     * 方法功能:查询所有未处理的告警
     * 参数:无
     * 返回数据:List<Alert>-告警集合
     * */
    @Override
    public List<Alert> findUnhandled() {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getIsHandled, 0);
        return list(wrapper);
    }

    /*
     * 方法功能:根据设备ID查询关联的告警
     * 参数:Integer deviceId-设备ID
     * 返回数据:List<Alert>-该设备关联的告警集合；如果参数为null则返回空集合Collections.emptyList()
     * */
    @Override
    public List<Alert> findByDeviceId(Integer deviceId) {
        if (deviceId == null){
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getDeviceId, deviceId);
        return list(wrapper);
    }

    /*
     * 方法功能:批量处理未处理的告警，并记录处理人和处理时间
     * 参数:List<Integer> ids-需要处理的告警ID集合
     *      String handleUser-处理人账号/姓名
     * 返回数据:int-成功处理的告警数量(受影响的行数)
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int batchHandle(List<Integer> ids, String handleUser) {
        // 防御性校验：空集合或 null 直接返回 0，避免执行无意义的 SQL
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LambdaUpdateWrapper<Alert> wrapper = new LambdaUpdateWrapper<Alert>()
                .in(Alert::getId, ids)
                .eq(Alert::getIsHandled, 0) // 乐观锁/状态校验：只处理未处理的告警，防止重复处理
                .set(Alert::getIsHandled, 1)
                .set(Alert::getHandleTime, LocalDateTime.now())
                .set(Alert::getHandleUser, handleUser);
        return baseMapper.update(null, wrapper);  // 直接返回 int
    }

    /*
     * 方法功能: 根据设备ID统计对应设备的告警数量、最高级别、平均级别、最低级别
     * 参数: Integer deviceId - 设备ID
     * 返回数据: Map<String, Object> - 包含 alertCount(告警总数), maxLevel(最高级别), avgLevel(平均级别), minLevel(最低级别) 四个键
     */
    public Map<String, Object> getStatsByDeviceId(Integer deviceId){
        //基础校验
        deviceService.getDeviceOrThrow(deviceId);

        //根据设备ID查询
        return baseMapper.getStatsByDeviceId(deviceId);
    }
    /*
     * 方法功能: 按时间区间统计区间内告警数量、最高级别、平均级别、最低级别
     * 参数: LocalDateTime startTime - 开始时间（包含）
     *       LocalDateTime endTime   - 结束时间（包含）
     * 返回数据: Map<String, Object> - 包含 alertCount, maxLevel, avgLevel, minLevel 四个键
     */
    public Map<String, Object> getStatsByTimeRange(LocalDateTime startTime,LocalDateTime endTime){
        return baseMapper.getStatsByTimeRange(startTime,endTime);
    }
    /*
     * 方法功能: 按时间区间统计所有设备的告警数量，以告警数量降序排序
     * 参数: LocalDateTime startTime - 开始时间（包含）
     *       LocalDateTime endTime   - 结束时间（包含）
     * 返回数据: List<Map<String, Object>> - 每条 Map 包含 deviceId(设备ID) 和 alertCount(告警数量)，按告警数量降序排列
     */
    public List<Map<String, Object>> listDeviceAlertCountByTimeRange(LocalDateTime startTime, LocalDateTime endTime){
        return baseMapper.listDeviceAlertCountByTimeRange(startTime,endTime);
    }
}
