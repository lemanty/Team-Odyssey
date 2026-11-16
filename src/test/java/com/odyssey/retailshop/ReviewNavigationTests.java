package com.odyssey.retailshop;

import com.odyssey.retailshop.service.ReviewService;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.HtmlUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewNavigationTests {
    @Autowired MockMvc mvc;
    @MockitoBean ReviewService reviews;

    private String visit(URI uri) throws Exception {
        return mvc.perform(get(uri)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private URI link(String html, String expression) {
        var match = Pattern.compile(expression).matcher(html);
        assertTrue(match.find(), expression);
        return URI.create(HtmlUtils.htmlUnescape(match.group(1)).split("#")[0]);
    }

    @Test void returnLinksSurviveBothReviewPageDirections() throws Exception {
        when(reviews.available()).thenReturn(true);
        when(reviews.page(1L,0)).thenReturn(new ReviewService.Page(List.of(),0,null,true));
        when(reviews.page(1L,1)).thenReturn(new ReviewService.Page(List.of(),0,null,false));
        URI catalogue = URI.create("/products?category=Men&minPrice=20&maxPrice=30");
        String list = visit(catalogue);
        URI detail = link(list, "href=\"(/products/1[?][^\"]*)\"");
        String pageOne = visit(detail);
        String pageTwo = visit(link(pageOne, "href=\"([^\"]*reviewPage=1[^\"]*)\""));
        String previous = visit(link(pageTwo, "href=\"([^\"]*reviewPage=0[^\"]*)\""));
        for (String page : List.of(pageTwo, previous)) {
            for (String selector : List.of("class=\"detail-back\"[^>]*", "class=\"product-breadcrumb\"[^>]*>\\s*<a[^>]*?")) {
                URI back = link(page,selector+"href=\"([^\"]+)\"");
                assertEquals(catalogue,back);
                assertTrue(visit(back).contains("Everyday Cotton Tee"));
            }
        }
    }
}
