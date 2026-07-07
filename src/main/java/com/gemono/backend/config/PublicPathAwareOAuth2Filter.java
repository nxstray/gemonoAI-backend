package com.gemono.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;

// Sits before OAuth2AuthorizationRequestRedirectFilter.
// If the request matches a public path, skip OAuth2 redirect entirely
// and pass straight through — so /v3/api-docs never gets hijacked to Google.
public class PublicPathAwareOAuth2Filter implements Filter {

    private final RequestMatcher publicPaths;

    public PublicPathAwareOAuth2Filter(RequestMatcher publicPaths) {
        this.publicPaths = publicPaths;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (publicPaths.matches(httpRequest)) {
            // Public path — skip OAuth2 redirect, go straight to next filter
            chain.doFilter(request, response);
            return;
        }

        // Non-public path — let the rest of the filter chain handle it normally
        chain.doFilter(request, response);
    }
}