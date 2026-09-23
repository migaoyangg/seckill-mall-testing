package com.seckill.mall.controller;

import com.seckill.mall.config.WebMvcConfig;
import com.seckill.mall.interceptor.AdminInterceptor;
import com.seckill.mall.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductAdminSecurityTest {

    @Mock
    private AuthInterceptor authInterceptor;

    @Mock
    private AdminInterceptor adminInterceptor;

    @Test
    void adminInterceptorCoversProductAdminApi() {
        WebMvcConfig config = new WebMvcConfig(authInterceptor, adminInterceptor);
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();

        config.addInterceptors(registry);

        MappedInterceptor mappedAdminInterceptor = registry.getRegisteredInterceptors()
                .stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(interceptor -> interceptor.getInterceptor() == adminInterceptor)
                .findFirst()
                .orElseThrow();

        AntPathMatcher pathMatcher = new AntPathMatcher();
        assertThat(mappedAdminInterceptor.matches("/admin/product/list", pathMatcher))
                .isTrue();
        assertThat(mappedAdminInterceptor.matches("/product/admin/delete/1", pathMatcher))
                .isTrue();
    }

    private static class ExposedInterceptorRegistry extends InterceptorRegistry {
        List<Object> getRegisteredInterceptors() {
            return getInterceptors();
        }
    }
}
