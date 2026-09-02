package com.example.platform.ingest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.ingest.entity.IngestPayloadMapping;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:自定义报文映射模板数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface IngestPayloadMappingMapper extends BaseMapper<IngestPayloadMapping> {
}
