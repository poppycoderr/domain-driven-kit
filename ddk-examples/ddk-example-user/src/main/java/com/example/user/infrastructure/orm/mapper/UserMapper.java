package com.example.user.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.user.infrastructure.orm.po.UserPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
}
