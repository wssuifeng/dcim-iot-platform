package com.example.platform.ingest.service;

import com.example.platform.dto.DeviceImportResult;
import com.example.platform.ingest.dto.IngestInventoryDTO;
import com.example.platform.ingest.dto.IngestInventoryResultVO;
import com.example.platform.ingest.entity.IngestCredential;

/*
 * 类功能:网关设备清单服务接口（清单上报/导入，接入打通的辅助通道）
 *
 * 【与首包自动注册的关系】自动注册零配置免维护；清单上报是显式通道：
 *   auto_register=1 时导入落库，否则仅返回 unknown 比对结果供前端人工导入。
 * */
public interface IngestInventoryService {

    /*
     * 方法功能:处理网关上报的本地设备清单
     * 参数:credential-已验签的网关凭证, inventory-清单载荷
     * 返回数据:IngestInventoryResultVO-逐设备结果（registered/exists/failed/unknown）
     * */
    IngestInventoryResultVO handleInventory(IngestCredential credential, IngestInventoryDTO inventory);
}
