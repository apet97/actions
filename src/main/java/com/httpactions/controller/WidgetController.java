package com.httpactions.controller;

import com.httpactions.model.dto.IframeViewContext;
import com.httpactions.model.dto.VerifiedAddonContext;
import com.httpactions.model.dto.WidgetStatusResponse;
import com.httpactions.model.dto.WidgetStats;
import com.httpactions.service.IframeViewSupport;
import com.httpactions.service.LogService;
import com.httpactions.service.WidgetPresentation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WidgetController {

    private final IframeViewSupport iframeViewSupport;
    private final LogService logService;

    public WidgetController(IframeViewSupport iframeViewSupport, LogService logService) {
        this.iframeViewSupport = iframeViewSupport;
        this.logService = logService;
    }

    @GetMapping("/widget")
    public String widget(@RequestParam(name = "auth_token", required = false) String authToken,
                         Model model, HttpServletResponse response) {
        IframeViewContext viewContext = iframeViewSupport.buildContext(authToken, response, "Widget");
        WidgetStatusResponse status = buildWidgetStatus(viewContext.workspaceId());

        model.addAttribute("theme", viewContext.theme());
        model.addAttribute("isDark", viewContext.dark());
        model.addAttribute("workspaceId", viewContext.workspaceId());
        model.addAttribute("stats", status.stats());
        model.addAttribute("status", status);
        model.addAttribute("rateClass", status.rateClass());
        model.addAttribute("rateBadgePrefix", status.rateBadgePrefix());
        model.addAttribute("language", viewContext.language());
        model.addAttribute("timezone", viewContext.timezone());

        return "widget";
    }

    @GetMapping("/api/widget/stats")
    @ResponseBody
    public WidgetStatusResponse widgetStats(VerifiedAddonContext addonContext) {
        return buildWidgetStatus(addonContext.workspaceId());
    }

    private WidgetStatusResponse buildWidgetStatus(String workspaceId) {
        WidgetStats stats = (workspaceId == null || workspaceId.isBlank())
                ? new WidgetStats(0, 0, 0, 0.0)
                : logService.getWidgetStats(workspaceId);
        return new WidgetStatusResponse(
                stats,
                WidgetPresentation.rateClass(stats.successRate24h()),
                WidgetPresentation.rateBadgePrefix(stats.successRate24h())
        );
    }
}
