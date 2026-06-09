package com.heypickler.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;

/**
 * XSS protection: escapes HTML in request parameters.
 * For JSON body fields, rely on Jackson's HtmlUtils escaping at the VO level
 * and frontend rendering (Vue auto-escapes by default).
 */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new XssRequestWrapper((HttpServletRequest) request), response);
    }

    static class XssRequestWrapper extends HttpServletRequestWrapper {
        XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return value != null ? sanitize(value) : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] escaped = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escaped[i] = sanitize(values[i]);
            }
            return escaped;
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return value != null ? sanitize(value) : null;
        }
    }

    static String sanitize(String value) {
        if (value == null || value.isEmpty()) return value;
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
