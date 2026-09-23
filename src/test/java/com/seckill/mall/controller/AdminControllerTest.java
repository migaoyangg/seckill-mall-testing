package com.seckill.mall.controller;

import com.seckill.mall.exception.GlobalExceptionHandler;
import com.seckill.mall.common.Constants;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.entity.User;
import com.seckill.mall.interceptor.AdminInterceptor;
import com.seckill.mall.interceptor.AuthInterceptor;
import com.seckill.mall.mapper.UserMapper;
import com.seckill.mall.service.OrderService;
import com.seckill.mall.service.ProductService;
import com.seckill.mall.service.SeckillService;
import com.seckill.mall.utils.JwtUtils;
import com.seckill.mall.utils.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private OrderService orderService;

    @Mock
    private SeckillService seckillService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RedisUtils redisUtils;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(
                productService,
                orderService,
                seckillService,
                userMapper
        );

        AuthInterceptor authInterceptor =
                new AuthInterceptor(jwtUtils, redisUtils);

        AdminInterceptor adminInterceptor =
                new AdminInterceptor(jwtUtils, redisUtils);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(authInterceptor, adminInterceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unauthenticatedUserCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/admin/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1001)));

        verifyNoInteractions(
                productService,
                orderService,
                seckillService,
                userMapper
        );
    }

    @Test
    void normalUserCannotAccessAdminApi() throws Exception {
        String token = "user-token";
        when(jwtUtils.getHeader()).thenReturn("Authorization");
        when(jwtUtils.getPrefix()).thenReturn("Bearer ");
        when(jwtUtils.parseToken(token)).thenReturn(7L);
        when(redisUtils.get("user:token:7")).thenReturn(token);

        User user = new User();
        user.setId(7L);
        user.setRole(Constants.ROLE_USER);
        when(redisUtils.getObject("user:info:7", User.class)).thenReturn(user);

        mockMvc.perform(get("/admin/product/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1002)));

        verifyNoInteractions(
                productService,
                orderService,
                seckillService,
                userMapper
        );
    }

    @Test
    void adminUserCanAccessAdminApi() throws Exception {
        String token = "admin-token";
        when(jwtUtils.getHeader()).thenReturn("Authorization");
        when(jwtUtils.getPrefix()).thenReturn("Bearer ");
        when(jwtUtils.parseToken(token)).thenReturn(1L);
        when(redisUtils.get("user:token:1")).thenReturn(token);

        User admin = new User();
        admin.setId(1L);
        admin.setRole(Constants.ROLE_ADMIN);
        when(redisUtils.getObject("user:info:1", User.class)).thenReturn(admin);
        
        when(productService.listAdminProducts(any()))
                .thenReturn(new PageResult<>(0L, java.util.List.of(), 1, 10));

        mockMvc.perform(get("/admin/product/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.page", is(1)))
                .andExpect(jsonPath("$.data.size", is(10)));

        verify(productService).listAdminProducts(any());
    }
}
