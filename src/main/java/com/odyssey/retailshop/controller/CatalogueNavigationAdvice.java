package com.odyssey.retailshop.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Set;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UriComponentsBuilder;

@ControllerAdvice
public class CatalogueNavigationAdvice {
    private static final Set<String> FILTERS = Set.of(
        "q", "category", "subcategory", "priceRange", "minPrice", "maxPrice",
        "sort", "size", "color", "inStock", "page");

    @ModelAttribute("catalogue")
    public String catalogue(HttpServletRequest request) {
        var result = UriComponentsBuilder.fromPath("/products");
        var values = new LinkedHashMap<String, String>();
        if (request.getRequestURI().equals(request.getContextPath() + "/products")) {
            for (String filter : FILTERS) {
                String value = request.getParameter(filter);
                if (value != null && value.length() <= 300) values.put(filter, value);
            }
        } else {
            String source = request.getParameter("catalogue");
            if (source != null && source.length() <= 4000 && source.startsWith("/products?")) {
                try {
                    var query = UriComponentsBuilder.fromUriString(source).build().getQueryParams();
                    for (String filter : FILTERS) {
                        String value = query.getFirst(filter);
                        if (value != null && value.length() <= 900) {
                            values.put(filter, org.springframework.web.util.UriUtils.decode(value, java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    return "/products";
                }
            }
        }
        values.keySet().forEach(filter -> result.queryParam(filter, "{" + filter + "}"));
        return result.encode().buildAndExpand(values).toUriString();
    }
}
