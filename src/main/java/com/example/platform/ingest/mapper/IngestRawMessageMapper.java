package com.example.platform.ingest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.ingest.entity.IngestRawMessage;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:开放通道原始报文留白数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface IngestRawMessageMapper extends BaseMapper<IngestRawMessage> {
}
