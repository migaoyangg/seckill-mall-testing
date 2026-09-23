package com.seckill.mall.config;

import com.seckill.mall.interceptor.AdminInterceptor;
import com.seckill.mall.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/register",
                        "/user/login",
                        "/product/list",
                        "/product/detail/**",
                        "/product/search",
                        "/seckill/list",
                        "/seckill/detail/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**"
                );

        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**", "/product/admin/**");
    }
}
