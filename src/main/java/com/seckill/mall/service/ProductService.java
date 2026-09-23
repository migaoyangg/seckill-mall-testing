package com.seckill.mall.service;

import com.seckill.mall.common.PageResult;
import com.seckill.mall.dto.ProductDTO;
import com.seckill.mall.dto.ProductQueryDTO;
import com.seckill.mall.vo.ProductVO;

public interface ProductService {

    PageResult<ProductVO> listProducts(ProductQueryDTO queryDTO);

    PageResult<ProductVO> listAdminProducts(ProductQueryDTO queryDTO);

    ProductVO getProductDetail(Long id);

    PageResult<ProductVO> searchProducts(String keyword, Integer page, Integer size);

    void addProduct(ProductDTO dto);

    void updateProduct(ProductDTO dto);

    void deleteProduct(Long id);

    void updateProductStatus(Long id, Integer status);
}
