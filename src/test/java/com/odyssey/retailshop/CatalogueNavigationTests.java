package com.odyssey.retailshop;

import com.odyssey.retailshop.controller.CatalogueNavigationAdvice;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueNavigationTests {
    @Test void preservesEncodedSearchAndFiltersAcrossDetailPages() {
        var advice = new CatalogueNavigationAdvice();
        var list = new MockHttpServletRequest("GET", "/products");
        list.addParameter("q", "棉 & blue");
        list.addParameter("page", "2");
        list.addParameter("category", "Men");
        String catalogue = advice.catalogue(list);
        var detail = new MockHttpServletRequest("GET", "/products/1");
        detail.addParameter("catalogue", catalogue);
        detail.addParameter("reviewPage", "1");
        assertEquals(catalogue, advice.catalogue(detail));
        assertFalse(catalogue.contains("reviewPage"));
    }
    @Test void preservesLiteralPlusThroughShopLinksAndFormDecoding() {
        var advice = new CatalogueNavigationAdvice();
        var list = new MockHttpServletRequest("GET", "/products");
        list.addParameter("q", "C++ & 棉");
        var detail = new MockHttpServletRequest("GET", "/products/1");
        detail.addParameter("catalogue", advice.catalogue(list));

        var back = URI.create(advice.catalogue(detail));
        assertEquals("q=C%2B%2B%20%26%20%E6%A3%89", back.getRawQuery());
        assertEquals("q=C++ & 棉", URLDecoder.decode(back.getRawQuery(), StandardCharsets.UTF_8));
    }

    @Test void refusesExternalReturnPaths() {
        var request = new MockHttpServletRequest("GET", "/products/1");
        request.addParameter("catalogue", "https://example.test/");
        assertEquals("/products", new CatalogueNavigationAdvice().catalogue(request));
    }
}
