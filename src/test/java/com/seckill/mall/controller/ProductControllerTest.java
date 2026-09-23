package com.seckill.mall.controller;

import com.seckill.mall.exception.GlobalExceptionHandler;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.service.ProductService;
import com.seckill.mall.vo.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getProductDetailReturnsProductJson() throws Exception {
        ProductVO product = new ProductVO();
        product.setId(1L);
        product.setName("测试商品");
        when(productService.getProductDetail(1L)).thenReturn(product);

        mockMvc.perform(get("/product/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.name", is("测试商品")));

        verify(productService).getProductDetail(1L);
    }

    @Test
    void getProductDetailReturnsBusinessErrorWhenProductIsMissing() throws Exception {
        when(productService.getProductDetail(999L))
                .thenThrow(new com.seckill.mall.exception.BusinessException(
                        com.seckill.mall.common.ResultCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/product/detail/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(3002)));
    }

    @Test
    void searchProductsPassesQueryParametersToService() throws Exception {
        when(productService.searchProducts("phone", 2, 5))
                .thenReturn(new PageResult<>(0L, java.util.List.of(), 2, 5));

        mockMvc.perform(get("/product/search")
                        .param("keyword", "phone")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.page", is(2)))
                .andExpect(jsonPath("$.data.size", is(5)));

        verify(productService).searchProducts("phone", 2, 5);
    }

    @Test
    void searchProductsRejectsMissingKeyword() throws Exception {
        mockMvc.perform(get("/product/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(2001)));
    }
 
    @Test
    void searchProductsUsesDefaultPageAndSize() throws Exception {
        when(productService.searchProducts("phone", 1, 10))
                .thenReturn(new PageResult<>(0L, java.util.List.of(), 1, 10));

        mockMvc.perform(get("/product/search")
                        .param("keyword", "phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.page", is(1)))
                .andExpect(jsonPath("$.data.size", is(10)));

        verify(productService).searchProducts("phone", 1, 10);
    }
    @Test
    void searchProductsRejectsNonNumericPage() throws Exception {
        mockMvc.perform(get("/product/search")
                        .param("keyword", "phone")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(2001)));
    }
    
}
