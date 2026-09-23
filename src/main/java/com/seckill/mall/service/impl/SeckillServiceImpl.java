package com.seckill.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.mall.common.Constants;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.dto.SeckillOrderMessage;
import com.seckill.mall.dto.SeckillGoodsDTO;
import com.seckill.mall.entity.Product;
import com.seckill.mall.entity.SeckillGoods;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.ProductMapper;
import com.seckill.mall.mapper.SeckillGoodsMapper;
import com.seckill.mall.mq.SeckillOrderProducer;
import com.seckill.mall.service.SeckillService;
import com.seckill.mall.utils.OrderNoUtils;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.SeckillGoodsVO;
import com.seckill.mall.vo.SeckillResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final ProductMapper productMapper;
    private final RedisUtils redisUtils;
    private final SeckillOrderProducer seckillOrderProducer;

    @Override
    public PageResult<SeckillGoodsVO> listSeckillGoods(Integer page, Integer size) {
        LambdaQueryWrapper<SeckillGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SeckillGoods::getCreateTime);

        Page<SeckillGoods> pageParam = new Page<>(page, size);
        Page<SeckillGoods> result = seckillGoodsMapper.selectPage(pageParam, wrapper);

        List<SeckillGoodsVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), voList, page, size);
    }

    @Override
    public SeckillGoodsVO getSeckillDetail(Long id) {
        String cacheKey = Constants.REDIS_SECKILL_GOODS_PREFIX + id;
        SeckillGoods seckillGoods = redisUtils.getObject(cacheKey, SeckillGoods.class);

        if (seckillGoods == null) {
            seckillGoods = seckillGoodsMapper.selectById(id);
            if (seckillGoods == null) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "秒杀活动不存在");
            }
        }

        SeckillGoodsVO vo = convertToVO(seckillGoods);
        Long remainStock = redisUtils.getSeckillStock(id);
        vo.setRemainStock(remainStock != null ? remainStock.intValue() : seckillGoods.getStockCount());
        return vo;
    }

    @Override
    public SeckillResultVO doSeckill(Long seckillGoodsId, Long userId) {
        // 1. 校验活动状态
        SeckillGoods seckillGoods = checkSeckillStatus(seckillGoodsId);

        // 2. 用户维度限流, 防止单用户瞬时刷接口
        boolean allowed = redisUtils.acquireSeckillRateLimit(seckillGoodsId, userId, 1, 5);
        if (!allowed) {
            throw new BusinessException(ResultCode.SECKILL_FAIL, "请求过于频繁, 请稍后重试");
        }

        // 3. 防重复请求
        boolean isFirstRequest = redisUtils.setSeckillRepeatFlag(seckillGoodsId, userId);
        if (!isFirstRequest) {
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }

        // 4. Lua脚本预扣减
        Long remainStock = redisUtils.executeSeckillDeduct(
                seckillGoodsId, userId, seckillGoods.getLimitPerUser());

        if (remainStock == null) {
            redisUtils.deleteSeckillRepeatFlag(seckillGoodsId, userId);
            throw new BusinessException(ResultCode.SECKILL_FAIL, "系统繁忙, 请稍后重试");
        }
        if (remainStock == -1L) {
            redisUtils.deleteSeckillRepeatFlag(seckillGoodsId, userId);
            throw new BusinessException(ResultCode.SECKILL_STOCK_EMPTY);
        }
        if (remainStock == -2L) {
            throw new BusinessException(ResultCode.SECKILL_LIMIT);
        }

        log.info("Redis扣减成功: goodsId={}, userId={}, remainStock={}",
                seckillGoodsId, userId, remainStock);

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String orderNo = OrderNoUtils.generateOrderNo();
        SeckillResultVO processingResult = SeckillResultVO.processing(
                requestId, orderNo, remainStock.intValue());

        try {
            redisUtils.setSeckillResult(requestId, processingResult);
            redisUtils.bindSeckillRequestUser(requestId, userId);
            seckillOrderProducer.send(SeckillOrderMessage.of(requestId, orderNo, seckillGoodsId, userId));
            log.info("秒杀请求已入队: requestId={}, goodsId={}, userId={}, orderNo={}, remainStock={}",
                    requestId, seckillGoodsId, userId, orderNo, remainStock);
            return processingResult;
        } catch (BusinessException e) {
            redisUtils.rollbackSeckillDeduct(seckillGoodsId, userId);
            redisUtils.deleteSeckillRepeatFlag(seckillGoodsId, userId);
            throw e;
        } catch (Exception e) {
            log.error("秒杀入队异常, 回滚预扣: goodsId={}, userId={}", seckillGoodsId, userId, e);
            redisUtils.rollbackSeckillDeduct(seckillGoodsId, userId);
            redisUtils.deleteSeckillRepeatFlag(seckillGoodsId, userId);
            throw new BusinessException(ResultCode.SECKILL_FAIL, "系统繁忙, 请稍后重试");
        }
    }

    @Override
    public SeckillResultVO getSeckillResult(String requestId) {
        SeckillResultVO result = redisUtils.getSeckillResult(requestId);
        if (result == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "秒杀结果不存在或已过期");
        }
        return result;
    }

    @Override
    public SeckillResultVO getSeckillResult(String requestId, Long userId) {
        Long ownerId = redisUtils.getSeckillRequestUser(requestId);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "秒杀结果不存在或无权查看");
        }
        return getSeckillResult(requestId);
    }

    private SeckillGoods checkSeckillStatus(Long seckillGoodsId) {
        String cacheKey = Constants.REDIS_SECKILL_GOODS_PREFIX + seckillGoodsId;
        SeckillGoods seckillGoods = redisUtils.getObject(cacheKey, SeckillGoods.class);

        if (seckillGoods == null) {
            seckillGoods = seckillGoodsMapper.selectById(seckillGoodsId);
            if (seckillGoods == null) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "秒杀活动不存在");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckillGoods.getStartTime())) {
            throw new BusinessException(ResultCode.SECKILL_NOT_START);
        }
        if (now.isAfter(seckillGoods.getEndTime())) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }
        if (seckillGoods.getStatus() != Constants.SECKILL_IN_PROGRESS) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }

        return seckillGoods;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSeckillGoods(SeckillGoodsDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (dto.getSeckillPrice().compareTo(product.getPrice()) >= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "秒杀价格必须低于原价");
        }
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }

        SeckillGoods seckillGoods = new SeckillGoods();
        BeanUtils.copyProperties(dto, seckillGoods);
        seckillGoods.setOriginalPrice(product.getPrice());

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dto.getStartTime())) {
            seckillGoods.setStatus(Constants.SECKILL_NOT_START);
        } else if (now.isAfter(dto.getEndTime())) {
            seckillGoods.setStatus(Constants.SECKILL_ENDED);
        } else {
            seckillGoods.setStatus(Constants.SECKILL_IN_PROGRESS);
        }

        seckillGoodsMapper.insert(seckillGoods);
        log.info("创建秒杀活动: id={}, productId={}", seckillGoods.getId(), dto.getProductId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeckillGoods(SeckillGoodsDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "秒杀商品ID不能为空");
        }
        SeckillGoods existing = seckillGoodsMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "秒杀活动不存在");
        }
        if (existing.getStatus() == Constants.SECKILL_IN_PROGRESS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "进行中的活动不允许修改");
        }

        SeckillGoods update = new SeckillGoods();
        BeanUtils.copyProperties(dto, update);
        seckillGoodsMapper.updateById(update);
        redisUtils.delete(Constants.REDIS_SECKILL_GOODS_PREFIX + dto.getId());
        log.info("修改秒杀活动: id={}", dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSeckillGoods(Long id) {
        SeckillGoods existing = seckillGoodsMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "秒杀活动不存在");
        }
        if (existing.getStatus() == Constants.SECKILL_IN_PROGRESS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "进行中的活动不允许删除");
        }

        seckillGoodsMapper.deleteById(id);
        redisUtils.delete(Constants.REDIS_SECKILL_GOODS_PREFIX + id);
        redisUtils.delete(Constants.REDIS_SECKILL_STOCK_PREFIX + id);
        log.info("删除秒杀活动: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeckillStatus(Long id, Integer status) {
        SeckillGoods existing = seckillGoodsMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "秒杀活动不存在");
        }

        SeckillGoods update = new SeckillGoods();
        update.setId(id);
        update.setStatus(status);
        seckillGoodsMapper.updateById(update);

        redisUtils.delete(Constants.REDIS_SECKILL_GOODS_PREFIX + id);

        if (status == Constants.SECKILL_IN_PROGRESS) {
            redisUtils.preheatStock(id, existing.getStockCount());
        }

        if (status == Constants.SECKILL_ENDED) {
            Long remainStock = redisUtils.getSeckillStock(id);
            if (remainStock != null) {
                SeckillGoods stockUpdate = new SeckillGoods();
                stockUpdate.setId(id);
                stockUpdate.setStockCount(remainStock.intValue());
                seckillGoodsMapper.updateById(stockUpdate);
            }
            redisUtils.delete(Constants.REDIS_SECKILL_STOCK_PREFIX + id);
        }

        log.info("更新秒杀活动状态: id={}, status={}", id, status);
    }

    private SeckillGoodsVO convertToVO(SeckillGoods seckillGoods) {
        SeckillGoodsVO vo = new SeckillGoodsVO();
        BeanUtils.copyProperties(seckillGoods, vo);

        Product product = productMapper.selectById(seckillGoods.getProductId());
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductImage(product.getMainImage());
        }

        if (seckillGoods.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discount = seckillGoods.getSeckillPrice()
                    .divide(seckillGoods.getOriginalPrice(), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.TEN);
            vo.setDiscount(discount.toPlainString() + "折");
        }

        vo.setStatusDesc(getStatusDesc(seckillGoods));

        Long remainStock = redisUtils.getSeckillStock(seckillGoods.getId());
        vo.setRemainStock(remainStock != null ? remainStock.intValue() : seckillGoods.getStockCount());

        return vo;
    }

    private String getStatusDesc(SeckillGoods seckillGoods) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        if (now.isBefore(seckillGoods.getStartTime())) {
            return "未开始 (" + seckillGoods.getStartTime().format(formatter) + "开始)";
        } else if (now.isAfter(seckillGoods.getEndTime())) {
            return "已结束";
        } else {
            return "进行中";
        }
    }
}
