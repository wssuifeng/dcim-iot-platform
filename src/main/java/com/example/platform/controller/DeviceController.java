package com.example.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.example.platform.entity.Device;
import com.example.platform.service.DeviceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Result<List<Device>> list() {
        List<Device> devices = deviceService.list();
        return Result.success(devices);
    }

    @GetMapping("/{id}")
        public Result<Device> getById(@PathVariable Integer id) {
        return Result.success(deviceService.getDeviceOrThrow(id));
    }

    @PostMapping
    public Result<Void> save(@Validated(CreateGroup.class) @RequestBody Device device) {
        boolean saved = deviceService.save(device);
        if (!saved) {
            return Result.error("新增设备失败");
        }
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id,
                               @Validated(UpdateGroup.class) @RequestBody Device device) {
        // 1. 先确保记录存在（不存在会抛异常，由全局异常处理器转 404）
        deviceService.getDeviceOrThrow(id);

        // 2. 防御性校验：body 里如果带了 id，必须和 path 一致；最终以 path id 为准
        if (device.getId() != null && !id.equals(device.getId())) {
            return Result.error("请求ID不一致，无法更新");
        }
        device.setId(id);

        // 3. 执行更新（若用了乐观锁，此处返回 false 表示并发冲突）
        boolean updated = deviceService.updateById(device);
        if (!updated) {
            return Result.error("更新设备失败，数据可能已被他人修改");
        }

        return Result.success();
    }
    @GetMapping("/online")
    public Result<List<Device>> onlineList() {
        List<Device> devices = deviceService.findOnlineDevices();
        return Result.success(devices);
    }

    @GetMapping("/search")
    public Result<List<Device>> search(@RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            return Result.error(400, "搜索关键词不能为空");
        }
        List<Device> devices = deviceService.findByNameLike(name);
        return Result.success(devices);
    }
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Integer id,
            @RequestParam Integer status) {
        if (status < 0 || status > 2) {
            return Result.error(400, "状态值无效，允许值：0-离线，1-在线，2-故障");
        }
        boolean updated = deviceService.updateStatus(id, status);
        if (!updated) {
            return Result.error("更新状态失败");
        }
        return Result.success();
    }

    @GetMapping("/page")
    public Result<Page<Device>> listByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String name) {
        Page<Device> page = new Page<>(current, size);
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Device::getStatus, status);
        }
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(Device::getDeviceName, name);
        }
        wrapper.orderByDesc(Device::getUpdateTime);
        Page<Device> result = deviceService.page(page, wrapper);
        return Result.success(result);
    }
    /*
     *  按id删除
     *  */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        deviceService.getDeviceOrThrow(id);
        deviceService.removeById(id);
        return Result.success();
    }
}
