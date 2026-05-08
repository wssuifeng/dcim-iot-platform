package com.example.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.Alert;
import com.example.platform.mapper.AlertMapper;
import com.example.platform.service.AlertService;
import org.springframework.stereotype.Service;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {
}
