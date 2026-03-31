package com.httpactions.config;

import com.httpactions.controller.UnauthorizedAddonException;
import com.httpactions.model.dto.VerifiedAddonContext;
import com.httpactions.service.VerifiedAddonContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class VerifiedAddonContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final VerifiedAddonContextService verifiedAddonContextService;

    public VerifiedAddonContextArgumentResolver(VerifiedAddonContextService verifiedAddonContextService) {
        this.verifiedAddonContextService = verifiedAddonContextService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return VerifiedAddonContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new UnauthorizedAddonException("Missing required header: X-Addon-Token");
        }

        Object cached = request.getAttribute(VerifiedAddonContext.REQUEST_ATTRIBUTE);
        if (cached instanceof VerifiedAddonContext context && context.workspaceId() != null) {
            return context;
        }

        String token = request.getHeader("X-Addon-Token");
        if (token == null || token.isBlank()) {
            throw new UnauthorizedAddonException("Missing required header: X-Addon-Token");
        }

        return verifiedAddonContextService.verifyToken(token)
                .filter(context -> context.workspaceId() != null)
                .orElseThrow(() -> new UnauthorizedAddonException("Invalid or expired addon token"));
    }
}
