package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.Alert;
import com.example.platform.mapper.AlertMapper;
import com.example.platform.service.AlertService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {
    public List<Alert> findByDeviceId(Integer deviceId) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getDeviceId, deviceId)
                .orderByDesc(Alert::getCreateTime);
        return list(wrapper);
    }

    public List<Alert> findUnhandled() {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getIsHandled, 0)
                .orderByDesc(Alert::getAlertLevel)
                .orderByDesc(Alert::getCreateTime);
        return list(wrapper);
    }
}
