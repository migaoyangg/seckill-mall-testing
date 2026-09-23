package com.seckill.mall.service.impl;

import com.seckill.mall.common.Constants;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.dto.ProductDTO;
import com.seckill.mall.entity.Category;
import com.seckill.mall.entity.Product;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.CategoryMapper;
import com.seckill.mall.mapper.ProductMapper;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private RedisUtils redisUtils;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(productMapper, categoryMapper, redisUtils);
    }

    @Test
    void getProductDetailReturnsCachedValueWithoutDatabaseLookup() {
        ProductVO cached = new ProductVO();
        cached.setId(10L);
        cached.setName("Cached keyboard");
        when(redisUtils.getObject(Constants.REDIS_PRODUCT_DETAIL_PREFIX + 10L, ProductVO.class))
                .thenReturn(cached);

        ProductVO result = service.getProductDetail(10L);

        assertSame(cached, result);
        verify(productMapper, never()).selectById(10L);
    }

    @Test
    void getProductDetailLoadsProductAndCachesViewOnMiss() {
        Product product = product(10L, Constants.PRODUCT_ON_SHELF);
        Category category = new Category();
        category.setId(2L);
        category.setName("Electronics");
        when(redisUtils.getObject(Constants.REDIS_PRODUCT_DETAIL_PREFIX + 10L, ProductVO.class))
                .thenReturn(null);
        when(productMapper.selectById(10L)).thenReturn(product);
        when(categoryMapper.selectById(2L)).thenReturn(category);

        ProductVO result = service.getProductDetail(10L);

        assertEquals(10L, result.getId());
        assertEquals("Electronics", result.getCategoryName());
        verify(redisUtils).set(eq(Constants.REDIS_PRODUCT_DETAIL_PREFIX + 10L), eq(result),
                eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    void getProductDetailRejectsUnknownProduct() {
        when(redisUtils.getObject(Constants.REDIS_PRODUCT_DETAIL_PREFIX + 999L, ProductVO.class))
                .thenReturn(null);
        when(productMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getProductDetail(999L));

        assertEquals(ResultCode.PRODUCT_NOT_FOUND.getCode(), exception.getCode());
        verify(redisUtils, never()).set(any(), any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void updateProductRejectsMissingIdBeforeDatabaseAccess() {
        ProductDTO dto = new ProductDTO();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProduct(dto));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(productMapper, never()).selectById(any());
    }

    private static Product product(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setName("Keyboard");
        product.setPrice(new BigDecimal("19.99"));
        product.setCategoryId(2L);
        product.setStatus(status);
        return product;
    }
}
