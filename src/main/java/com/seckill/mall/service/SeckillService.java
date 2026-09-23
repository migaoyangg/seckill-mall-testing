package com.seckill.mall.service;

import com.seckill.mall.common.PageResult;
import com.seckill.mall.dto.SeckillGoodsDTO;
import com.seckill.mall.vo.SeckillGoodsVO;
import com.seckill.mall.vo.SeckillResultVO;

public interface SeckillService {

    PageResult<SeckillGoodsVO> listSeckillGoods(Integer page, Integer size);

    SeckillGoodsVO getSeckillDetail(Long id);

    SeckillResultVO doSeckill(Long seckillGoodsId, Long userId);

    SeckillResultVO getSeckillResult(String requestId);

    SeckillResultVO getSeckillResult(String requestId, Long userId);

    void createSeckillGoods(SeckillGoodsDTO dto);

    void updateSeckillGoods(SeckillGoodsDTO dto);

    void deleteSeckillGoods(Long id);

    void updateSeckillStatus(Long id, Integer status);
}
