package com.odyssey.retailshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Server-rendered markup checks; animation behavior is covered by the Node harness. */
@SpringBootTest
@AutoConfigureMockMvc
class HomeHeroMotionTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void homepageKeepsOriginalSceneAndProgressivelyEnhancesOnlyTheArtwork() throws Exception {
        String html = mockMvc.perform(get("/").param("lang", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-hero-motion")))
            .andExpect(content().string(containsString("src=\"/images/odyssey-voyage.png\"")))
            .andExpect(content().string(containsString("width=\"1254\" height=\"1254\"")))
            .andExpect(content().string(containsString("An ivory arch opening onto the Aegean Sea")))
            .andExpect(content().string(containsString("/css/hero-motion.css?v=sea-loop-2")))
            .andExpect(content().string(containsString("/js/hero-motion.js?v=sea-loop-2")))
            .andExpect(content().string(containsString("class=\"hero-scene\"")))
            .andExpect(content().string(containsString("data-sea-src=\"/images/odyssey-voyage-sea.png\"")))
            .andExpect(content().string(not(containsString("cursor-effects"))))
            .andExpect(content().string(containsString("href=\"/products\"")))
            .andReturn().getResponse().getContentAsString();
        assertTrue(!html.contains("hero-motion-toggle"), "Homepage autoplays without a playback control");
    }

    @Test
    void chineseHomepageKeepsOriginalDescriptionWithoutPlaybackControls() throws Exception {
        mockMvc.perform(get("/").param("lang", "zh"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("lang=\"zh-CN\"")))
            .andExpect(content().string(containsString("米白色拱门外是爱琴海与远处的帆船")))
            .andExpect(content().string(containsString("日常好物，")));
    }

    @Test
    void animationAssetsAreServedPublicly() throws Exception {
        mockMvc.perform(get("/js/hero-motion.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("prefers-reduced-motion: reduce")));
        mockMvc.perform(get("/css/hero-motion.css"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("prefers-reduced-motion:reduce")));
    }

    @Test
    void catalogueDoesNotLoadHomepageSpecificMotion() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/js/hero-motion.js"))))
            .andExpect(content().string(not(containsString("/css/hero-motion.css"))));
    }
}
