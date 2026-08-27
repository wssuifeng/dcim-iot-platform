package com.example.platform.ingest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.ingest.entity.IngestCredential;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:网关接入凭证数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface IngestCredentialMapper extends BaseMapper<IngestCredential> {
}
