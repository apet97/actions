package com.httpactions.service;

import com.httpactions.model.dto.IframeViewContext;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IframeViewSupport {

    private static final Logger log = LoggerFactory.getLogger(IframeViewSupport.class);

    public static final String CSP_HEADER = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' https://resources.developer.clockify.me; "
            + "style-src 'self' 'unsafe-inline' https://resources.developer.clockify.me; "
            + "font-src 'self' https://resources.developer.clockify.me; "
            + "img-src 'self' data: https://resources.developer.clockify.me; "
            + "connect-src 'self' https://resources.developer.clockify.me; "
            + "frame-ancestors https://*.clockify.me";

    private final VerifiedAddonContextService verifiedAddonContextService;

    public IframeViewSupport(VerifiedAddonContextService verifiedAddonContextService) {
        this.verifiedAddonContextService = verifiedAddonContextService;
    }

    public IframeViewContext buildContext(String authToken, HttpServletResponse response, String viewName) {
        response.setHeader("Content-Security-Policy", CSP_HEADER);
        response.setHeader("Referrer-Policy", "no-referrer");

        if (authToken == null || authToken.isBlank()) {
            return new IframeViewContext("DEFAULT", false, "", "en", "");
        }

        return verifiedAddonContextService.verifyToken(authToken)
                .map(context -> new IframeViewContext(
                        context.theme(),
                        "DARK".equals(context.theme()),
                        context.workspaceId() == null ? "" : context.workspaceId(),
                        context.language(),
                        context.timezone()
                ))
                .orElseGet(() -> {
                    log.warn("{}: invalid or expired auth token", viewName);
                    return new IframeViewContext("DEFAULT", false, "", "en", "");
                });
    }
}
