package com.seckill.mall.controller;

import com.seckill.mall.annotation.AdminRequired;
import com.seckill.mall.annotation.LoginRequired;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.common.Result;
import com.seckill.mall.dto.SeckillGoodsDTO;
import com.seckill.mall.service.SeckillService;
import com.seckill.mall.vo.SeckillGoodsVO;
import com.seckill.mall.vo.SeckillResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "秒杀模块", description = "秒杀活动管理、秒杀下单")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "秒杀活动列表")
    @GetMapping("/list")
    public Result<PageResult<SeckillGoodsVO>> listSeckillGoods(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<SeckillGoodsVO> result = seckillService.listSeckillGoods(page, size);
        return Result.success(result);
    }

    @Operation(summary = "秒杀活动详情")
    @GetMapping("/detail/{id}")
    public Result<SeckillGoodsVO> getSeckillDetail(@PathVariable Long id) {
        SeckillGoodsVO vo = seckillService.getSeckillDetail(id);
        return Result.success(vo);
    }

    @Operation(summary = "参与秒杀")
    @LoginRequired
    @PostMapping("/do/{seckillGoodsId}")
    public Result<SeckillResultVO> doSeckill(@PathVariable Long seckillGoodsId,
                                             HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SeckillResultVO result = seckillService.doSeckill(seckillGoodsId, userId);
        return Result.success(result);
    }

    @Operation(summary = "查询秒杀结果")
    @LoginRequired
    @GetMapping("/result/{requestId}")
    public Result<SeckillResultVO> getSeckillResult(@PathVariable String requestId,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SeckillResultVO result = seckillService.getSeckillResult(requestId, userId);
        return Result.success(result);
    }

    @Operation(summary = "创建秒杀活动 (管理员)")
    @AdminRequired
    @PostMapping("/admin/create")
    public Result<Void> createSeckillGoods(@Valid @RequestBody SeckillGoodsDTO dto) {
        seckillService.createSeckillGoods(dto);
        return Result.success();
    }

    @Operation(summary = "修改秒杀活动 (管理员)")
    @AdminRequired
    @PutMapping("/admin/update")
    public Result<Void> updateSeckillGoods(@Valid @RequestBody SeckillGoodsDTO dto) {
        seckillService.updateSeckillGoods(dto);
        return Result.success();
    }

    @Operation(summary = "删除秒杀活动 (管理员)")
    @AdminRequired
    @DeleteMapping("/admin/delete/{id}")
    public Result<Void> deleteSeckillGoods(@PathVariable Long id) {
        seckillService.deleteSeckillGoods(id);
        return Result.success();
    }

    @Operation(summary = "更新秒杀活动状态 (管理员)")
    @AdminRequired
    @PutMapping("/admin/status/{id}/{status}")
    public Result<Void> updateSeckillStatus(@PathVariable Long id,
                                            @PathVariable Integer status) {
        seckillService.updateSeckillStatus(id, status);
        return Result.success();
    }
}
