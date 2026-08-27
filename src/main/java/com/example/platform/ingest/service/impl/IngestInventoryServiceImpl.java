package com.example.platform.ingest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.Device;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.entity.PointDefinition;
import com.example.platform.ingest.dto.IngestInventoryDTO;
import com.example.platform.ingest.dto.IngestInventoryResultVO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestInventoryService;
import com.example.platform.service.DevicePointBindingService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:网关设备清单服务实现（清单上报式接入）
 *
 * 【导入语义】auto_register=1 时逐台导入（已存在跳过并补齐缺失点位绑定）；
 *   否则仅返回 unknown 比对结果，由前端"主动提交清单"人工导入。
 * 【逐台独立】单台失败不阻塞其余（与采样批量语义一致）。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestInventoryServiceImpl implements IngestInventoryService {

    private final DeviceService deviceService;
    private final DevicePointBindingService devicePointBindingService;
    private final PointDefinitionService pointDefinitionService;
    private final com.example.platform.ingest.service.DeviceTypeInferencer typeInferencer;

    @Override
    public IngestInventoryResultVO handleInventory(IngestCredential credential,
                                                   IngestInventoryDTO inventory) {
        IngestInventoryResultVO result = new IngestInventoryResultVO();
        List<IngestInventoryResultVO.ItemResult> results = new ArrayList<>();
        // 类型解析三级：清单类型码 > 点位推断 > 凭证默认（均可空，解析失败逐台报错）
        boolean importEnabled = credential.getAutoRegister() != null
                && credential.getAutoRegister() == 1;

        for (IngestInventoryDTO.DeviceItem item : inventory.getDevices()) {
            IngestInventoryResultVO.ItemResult itemResult = new IngestInventoryResultVO.ItemResult();
            itemResult.setDeviceCode(item.getDeviceCode());
            try {
                Device existing = deviceService.findByCode(item.getDeviceCode());
                if (existing != null) {
                    int bound = bindMissingPoints(existing, item.getPointCodes());
                    itemResult.setResult("exists");
                    itemResult.setMessage("已存在，补建绑定" + bound + "个");
                } else if (importEnabled) {
                    Integer typeId = resolveTypeId(item, credential);
                    if (typeId == null) {
                        itemResult.setResult("failed");
                        itemResult.setMessage("无法确定设备类型（清单未带类型码、点位无法推断且凭证未配置默认类型）");
                        results.add(itemResult);
                        continue;
                    }
                    Device device = deviceService.autoRegisterDevice(
                            item.getDeviceCode(), credential.getCloudRoomId(),
                            typeId, credential.getId());
                    int bound = bindMissingPoints(device, item.getPointCodes());
                    itemResult.setResult("registered");
                    itemResult.setMessage("已建档（类型ID " + typeId + "），绑定" + bound + "个点位");
                } else {
                    itemResult.setResult("unknown");
                    itemResult.setMessage("平台未注册且凭证未开启自动注册");
                }
            } catch (Exception ex) {
                itemResult.setResult("failed");
                itemResult.setMessage(ex.getMessage());
                log.warn("清单设备处理失败 deviceCode={}", item.getDeviceCode(), ex);
            }
            results.add(itemResult);
        }

        result.setRegistered((int) results.stream().filter(r -> "registered".equals(r.getResult())).count());
        result.setExists((int) results.stream().filter(r -> "exists".equals(r.getResult())).count());
        result.setFailed((int) results.stream().filter(r -> "failed".equals(r.getResult())).count());
        result.setUnknown((int) results.stream().filter(r -> "unknown".equals(r.getResult())).count());
        result.setResults(results);
        log.info("网关清单处理完成 accessKey={} registered={} exists={} failed={} unknown={}",
                credential.getAccessKey(), result.getRegistered(), result.getExists(),
                result.getFailed(), result.getUnknown());
        return result;
    }

    // 类型优先级：清单显式类型码 > 上报点位推断 > 凭证默认类型
    private Integer resolveTypeId(IngestInventoryDTO.DeviceItem item, IngestCredential credential) {
        Integer byCode = typeInferencer.resolveByTypeCode(item.getDeviceTypeCode());
        if (byCode != null) {
            return byCode;
        }
        Integer byPoints = typeInferencer.inferByPointCodes(item.getPointCodes());
        if (byPoints != null) {
            return byPoints;
        }
        return credential.getDefaultTypeId();
    }

    // 补建缺失点位绑定（清单 pointCodes 与平台点位目录交集内），返回新建绑定数
    private int bindMissingPoints(Device device, List<String> pointCodes) {
        if (pointCodes == null || pointCodes.isEmpty()) {
            return 0;
        }
        List<Long> existPointIds = new ArrayList<>(devicePointBindingService.list(
                        new LambdaQueryWrapper<DevicePointBinding>()
                                .eq(DevicePointBinding::getDeviceId, device.getId()))
                .stream().map(DevicePointBinding::getPointId).toList());
        int bound = 0;
        for (String pointCode : pointCodes) {
            PointDefinition point = pointDefinitionService.getOne(new LambdaQueryWrapper<PointDefinition>()
                    .eq(PointDefinition::getPointCode, pointCode));
            if (point == null || existPointIds.contains(point.getId())) {
                continue;
            }
            DevicePointBinding binding = new DevicePointBinding();
            binding.setDeviceId(device.getId());
            binding.setPointId(point.getId());
            binding.setStatus(0);
            devicePointBindingService.save(binding);
            existPointIds.add(point.getId());
            bound++;
        }
        return bound;
    }
}
