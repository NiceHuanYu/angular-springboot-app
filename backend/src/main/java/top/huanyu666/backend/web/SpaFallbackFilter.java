package top.huanyu666.backend.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把不含扩展名、非 /api 的 GET 请求转给前端路由，避免刷新深链接 404。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaFallbackFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean isFrontendRoute = "GET".equals(request.getMethod())
                && !path.startsWith("/api")
                && !path.contains(".");

        if (isFrontendRoute) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}