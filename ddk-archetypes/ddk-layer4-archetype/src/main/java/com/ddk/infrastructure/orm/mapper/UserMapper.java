package com.ddk.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddk.infrastructure.orm.po.UserPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Elijah Du
 * @date 2025/2/20
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
}
