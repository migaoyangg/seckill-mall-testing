package com.seckill.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.mall.common.Constants;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.dto.ProductDTO;
import com.seckill.mall.dto.ProductQueryDTO;
import com.seckill.mall.entity.Category;
import com.seckill.mall.entity.Product;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.CategoryMapper;
import com.seckill.mall.mapper.ProductMapper;
import com.seckill.mall.service.ProductService;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final RedisUtils redisUtils;

    @Override
    public PageResult<ProductVO> listProducts(ProductQueryDTO queryDTO) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, Constants.PRODUCT_ON_SHELF);
        return listProducts(queryDTO, wrapper);
    }

    @Override
    public PageResult<ProductVO> listAdminProducts(ProductQueryDTO queryDTO) {
        return listProducts(queryDTO, new LambdaQueryWrapper<>());
    }

    private PageResult<ProductVO> listProducts(ProductQueryDTO queryDTO, LambdaQueryWrapper<Product> wrapper) {
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, queryDTO.getCategoryId());
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.like(Product::getName, queryDTO.getKeyword());
        }
        if (StringUtils.hasText(queryDTO.getBrand())) {
            wrapper.eq(Product::getBrand, queryDTO.getBrand());
        }
        if (queryDTO.getMinPrice() != null) {
            wrapper.ge(Product::getPrice, queryDTO.getMinPrice());
        }
        if (queryDTO.getMaxPrice() != null) {
            wrapper.le(Product::getPrice, queryDTO.getMaxPrice());
        }

        String sortBy = queryDTO.getSortBy() != null ? queryDTO.getSortBy() : "createTime";
        boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
        switch (sortBy) {
            case "sales" -> wrapper.orderBy(true, isAsc, Product::getSales);
            case "price" -> wrapper.orderBy(true, isAsc, Product::getPrice);
            default -> wrapper.orderBy(true, isAsc, Product::getCreateTime);
        }

        Page<Product> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        Page<Product> result = productMapper.selectPage(page, wrapper);

        List<ProductVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), voList, queryDTO.getPage(), queryDTO.getSize());
    }

    @Override
    public ProductVO getProductDetail(Long id) {
        String cacheKey = Constants.REDIS_PRODUCT_DETAIL_PREFIX + id;
        ProductVO cached = redisUtils.getObject(cacheKey, ProductVO.class);
        if (cached != null) {
            return cached;
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        ProductVO vo = convertToVO(product);
        redisUtils.set(cacheKey, vo, 10, TimeUnit.MINUTES);
        return vo;
    }

    @Override
    public PageResult<ProductVO> searchProducts(String keyword, Integer page, Integer size) {
        ProductQueryDTO queryDTO = new ProductQueryDTO();
        queryDTO.setKeyword(keyword);
        queryDTO.setPage(page);
        queryDTO.setSize(size);
        return listProducts(queryDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProduct(ProductDTO dto) {
        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        product.setSales(0);
        product.setStatus(Constants.PRODUCT_ON_SHELF);
        product.setVersion(0);
        productMapper.insert(product);
        log.info("新增商品: id={}, name={}", product.getId(), product.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(ProductDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "商品ID不能为空");
        }
        Product existing = productMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        productMapper.updateById(product);
        redisUtils.delete(Constants.REDIS_PRODUCT_DETAIL_PREFIX + dto.getId());
        log.info("修改商品: id={}", dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        productMapper.deleteById(id);
        redisUtils.delete(Constants.REDIS_PRODUCT_DETAIL_PREFIX + id);
        log.info("删除商品: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProductStatus(Long id, Integer status) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        Product update = new Product();
        update.setId(id);
        update.setStatus(status);
        productMapper.updateById(update);
        redisUtils.delete(Constants.REDIS_PRODUCT_DETAIL_PREFIX + id);
        log.info("更新商品状态: id={}, status={}", id, status);
    }

    private ProductVO convertToVO(Product product) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        if (product.getCategoryId() != null) {
            Category category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        return vo;
    }
}
