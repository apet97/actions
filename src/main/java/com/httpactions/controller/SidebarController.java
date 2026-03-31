package com.httpactions.controller;

import com.httpactions.model.dto.IframeViewContext;
import com.httpactions.service.IframeViewSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SidebarController {

    private final IframeViewSupport iframeViewSupport;

    public SidebarController(IframeViewSupport iframeViewSupport) {
        this.iframeViewSupport = iframeViewSupport;
    }

    @GetMapping("/sidebar")
    public String sidebar(@RequestParam(name = "auth_token", required = false) String authToken,
                          Model model, HttpServletResponse response) {
        IframeViewContext viewContext = iframeViewSupport.buildContext(authToken, response, "Sidebar");
        model.addAttribute("theme", viewContext.theme());
        model.addAttribute("isDark", viewContext.dark());
        model.addAttribute("workspaceId", viewContext.workspaceId());
        model.addAttribute("language", viewContext.language());
        model.addAttribute("timezone", viewContext.timezone());
        return "sidebar";
    }
}
