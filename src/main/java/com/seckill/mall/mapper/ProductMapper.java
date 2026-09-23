package com.seckill.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.mall.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE product SET stock = stock - #{quantity}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND stock >= #{quantity} AND version = #{version}")
    int deductStock(@Param("id") Long id,
                    @Param("quantity") Integer quantity,
                    @Param("version") Integer version);
}
