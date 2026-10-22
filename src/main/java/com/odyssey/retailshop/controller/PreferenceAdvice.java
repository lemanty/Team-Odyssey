package com.odyssey.retailshop.controller;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class PreferenceAdvice {
    private final com.odyssey.retailshop.service.StoreText translations;
    public PreferenceAdvice(com.odyssey.retailshop.service.StoreText translations) { this.translations = translations; }
    @ModelAttribute("storeText")
    public com.odyssey.retailshop.service.StoreText translations() { return translations; }

    @ModelAttribute("englishLanguageUrl")
    public String englishUrl(HttpServletRequest request) { return languageUrl(request, "en"); }
    @ModelAttribute("chineseLanguageUrl")
    public String chineseUrl(HttpServletRequest request) { return languageUrl(request, "zh"); }
    private String languageUrl(HttpServletRequest request, String language) {
        // A query-only link stays on the current path and retains filters/pagination.
        return "?" + org.springframework.web.util.UriComponentsBuilder.newInstance()
            .query(request.getQueryString()).replaceQueryParam("lang", language)
            .build(true).getQuery();
    }
    @ModelAttribute("supportStaff") public boolean supportStaff(org.springframework.security.core.Authentication auth) {
        return auth!=null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
    }
    @ModelAttribute("isAdmin") public boolean isAdmin(org.springframework.security.core.Authentication auth) {
        return auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
    }
    @ModelAttribute("zh") public boolean chinese(HttpServletRequest request) { return Boolean.TRUE.equals(request.getAttribute("zh")); }
    @ModelAttribute("newAccountWindowUrl") public String newAccountWindowUrl() { return "/login?window="+java.util.UUID.randomUUID().toString().replace("-",""); }
    @ModelAttribute("signedIn") public boolean signedIn(Principal principal) { return principal != null; }
}
