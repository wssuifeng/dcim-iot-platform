package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.dto.DeviceDataBatchDTO;
import com.example.platform.entity.DeviceData;
import com.example.platform.service.DeviceDataService;
import com.example.platform.service.DeviceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备数据接口层，提供设备数据上报、查询、统计及分页等RESTful接口
 * */
@RestController
@RequestMapping("/device-data")
@Validated
public class DeviceDataController {

    private final DeviceDataService deviceDataService;
    private final DeviceService deviceService;

    public DeviceDataController(DeviceDataService deviceDataService,
                                DeviceService deviceService) {
        this.deviceDataService = deviceDataService;
        this.deviceService = deviceService;
    }

    /*
     * 方法功能:接收单条设备数据，保存并触发告警检查
     * 参数:deviceData-需符合CreateGroup校验规则的设备数据JSON对象
     * 返回数据:Result<Void>-操作结果
     * */
    @PostMapping
    public Result<Void> reportData(@Validated(CreateGroup.class) @RequestBody DeviceData deviceData) {
        deviceDataService.saveWithAlertCheck(deviceData);
        return Result.success();
    }

    /*
     * 方法功能:批量接收设备数据，逐条保存并触发告警检查
     * 参数:batchDTO-包含设备数据列表的批量传输对象
     * 返回数据:Result<Void>-操作结果
     * */
    @PostMapping("/batch")
    public Result<Void> reportBatch(@Validated @RequestBody DeviceDataBatchDTO batchDTO) {
        deviceDataService.saveBatchWithAlertCheck(batchDTO.getDataList());
        return Result.success();
    }

    /*
     * 方法功能:查询指定设备的分页历史数据，按采集时间倒序排列
     * 参数:deviceId-设备ID（路径参数）, current-当前页码, size-每页大小
     * 返回数据:Result<Page<DeviceData>>-设备历史数据的分页结果
     * */
    @GetMapping("/device/{deviceId}")
    public Result<Page<DeviceData>> listByDevice(
            @PathVariable Integer deviceId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        deviceService.getDeviceOrThrow(deviceId);
        return Result.success(deviceDataService.pageByDeviceId(deviceId, current, size));
    }

    /*
     * 方法功能:查询指定设备的最新采集数据
     * 参数:deviceId-设备ID（路径参数）
     * 返回数据:Result<DeviceData>-最新一条设备数据对象；无数据时返回404错误
     * */
    @GetMapping("/device/{deviceId}/latest")
    public Result<DeviceData> getLatest(@PathVariable Integer deviceId) {
        deviceService.getDeviceOrThrow(deviceId);
        DeviceData latest = deviceDataService.findLatestByDeviceId(deviceId);
        if (latest == null) {
            return Result.error(404, "该设备暂无采集数据");
        }
        return Result.success(latest);
    }

    /*
     * 方法功能:查询指定设备在某个时间段内的所有数据
     * 参数:deviceId-设备ID（路径参数）, startTime-开始时间, endTime-结束时间
     * 返回数据:Result<List<DeviceData>>-时间段内设备数据列表
     * */
    @GetMapping("/device/{deviceId}/range")
    public Result<List<DeviceData>> listByTimeRange(
            @PathVariable Integer deviceId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        deviceService.getDeviceOrThrow(deviceId);
        List<DeviceData> list = deviceDataService.findByDeviceIdAndTimeRange(
                deviceId, startTime, endTime);
        return Result.success(list);
    }

    /*
     * 方法功能:获取指定设备在时间段内的统计数据（如平均值、最大值、最小值等）
     * 参数:deviceId-设备ID（路径参数）, startTime-开始时间, endTime-结束时间
     * 返回数据:Result<Map<String, Object>>-包含统计指标的键值对
     * */
    @GetMapping("/device/{deviceId}/statistics")
    public Result<Map<String, Object>> getStatistics(
            @PathVariable Integer deviceId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        deviceService.getDeviceOrThrow(deviceId);
        Map<String, Object> stats = deviceDataService.getStatistics(
                deviceId, startTime, endTime);
        return Result.success(stats);
    }

    /*
     * 方法功能:分页查询设备数据，可选按设备ID筛选
     * 参数:current-当前页码, size-每页大小, deviceId-设备ID（可选）
     * 返回数据:Result<Page<DeviceData>>-分页查询结果
     * */
    @GetMapping("/page")
    public Result<Page<DeviceData>> listByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer deviceId) {
        return Result.success(deviceDataService.pageByCondition(deviceId, current, size));
    }
}
