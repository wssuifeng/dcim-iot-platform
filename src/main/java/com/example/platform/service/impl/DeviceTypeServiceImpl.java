package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.DeviceTypeNotFound;
import com.example.platform.entity.DeviceType;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Service;

@Service
public class DeviceTypeServiceImpl extends ServiceImpl<DeviceTypeMapper, DeviceType> implements DeviceTypeService {
    public void typeIdExists(int typeId){
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getId,typeId);

        if(count(wrapper) == 0 ){
            throw new DeviceTypeNotFound(typeId);
        }
    }

    public DeviceType findByTypeCode(String typeCode) {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getTypeCode, typeCode);
        return getOne(wrapper);
    }
}
