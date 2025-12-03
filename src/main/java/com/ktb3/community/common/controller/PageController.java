package com.ktb3.community.common.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/api/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/api/privacy")
    public String privacy() {
        return "privacy";
    }
}
