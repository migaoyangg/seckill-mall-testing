package com.seckill.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.mall.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Update("UPDATE seckill_goods SET stock_count = stock_count - 1, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE id = #{seckillGoodsId} AND stock_count > 0 AND version = #{version}")
    int deductSeckillStock(@Param("seckillGoodsId") Long seckillGoodsId,
                           @Param("version") Integer version);

    @Select("SELECT * FROM orders WHERE status = 0 AND deleted = 0 " +
            "AND ((is_seckill = 1 AND create_time < #{seckillTimeoutTime}) " +
            "OR (is_seckill = 0 AND create_time < #{normalTimeoutTime})) " +
            "LIMIT #{limit}")
    List<Order> selectTimeoutOrders(@Param("normalTimeoutTime") String normalTimeoutTime,
                                    @Param("seckillTimeoutTime") String seckillTimeoutTime,
                                    @Param("limit") Integer limit);
}
