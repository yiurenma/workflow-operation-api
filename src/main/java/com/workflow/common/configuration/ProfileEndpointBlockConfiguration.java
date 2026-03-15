package com.workflow.common.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class ProfileEndpointBlockConfiguration {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> blockSpringDataRestProfileEndpointsFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {
                String requestUri = request.getRequestURI();
                String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
                String profileRoot = contextPath + "/profile";
                if (requestUri.equals(profileRoot) || requestUri.startsWith(profileRoot + "/")) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };

        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registrationBean.addUrlPatterns("/profile", "/profile/*");
        return registrationBean;
    }
}
