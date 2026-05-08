package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.exception.DeviceNotFoundException;
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
        Device device = deviceService.getById(id);
        if (device == null) {
            // 直接抛出 DeviceNotFoundException，交给全局异常处理器
            throw new DeviceNotFoundException("设备不存在");
        }
        return Result.success(device);
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
    public Result<Void> update(@PathVariable Integer id, @Validated(UpdateGroup.class) @RequestBody Device device) {
        Device existing = deviceService.getById(id);
        if (existing == null) {
            return Result.error("设备不存在，无法更新");
        }
        if (!id.equals(device.getId())) {   // 注意：如果 device.getId() 为 null 需要特殊处理
            return Result.error("请求ID不一致，无法更新");
        }
        else device.setId(id);
        boolean updated = deviceService.updateById(device);
        if (!updated) {
            return Result.error("更新设备失败");
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
}
