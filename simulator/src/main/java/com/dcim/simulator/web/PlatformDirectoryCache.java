package com.dcim.simulator.web;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 类功能:平台机房目录缓存（多网关共享一份全量目录）
 *
 * 说明:单租户平台全量房间列表对所有凭证可见（来源为阈值导出接口附带数据），
 *   任一网关同步阈值时顺带刷新；内存态重启即失，启动预热兜底。
 * */
@Component
public class PlatformDirectoryCache {

    private volatile List<Map<String, Object>> rooms = new ArrayList<>();

    /** 平台机房目录（供管理页机房下拉） */
    public List<Map<String, Object>> rooms() {
        return rooms;
    }

    /** 刷新目录（原始导出行 → 管理页展示行） */
    public void refresh(List<Map<String, Object>> rawRooms) {
        if (rawRooms == null) {
            return;
        }
        List<Map<String, Object>> labeled = new ArrayList<>();
        for (Map<String, Object> room : rawRooms) {
            Map<String, Object> item = new HashMap<>();
            item.put("roomId", room.get("roomId"));
            item.put("label", "机房 #" + room.get("roomId"));
            labeled.add(item);
        }
        this.rooms = labeled;
    }
}
