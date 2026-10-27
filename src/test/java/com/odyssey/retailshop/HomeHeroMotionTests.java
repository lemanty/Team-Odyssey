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
            .andExpect(content().string(containsString("/css/hero-motion.css?v=1")))
            .andExpect(content().string(containsString("/js/hero-motion.js?v=1")))
            .andExpect(content().string(containsString("aria-controls=\"hero-voyage\"")))
            .andExpect(content().string(containsString("data-label-pause=\"Pause background\"")))
            .andExpect(content().string(containsString("data-label-play=\"Play background\"")))
            .andExpect(content().string(containsString("href=\"/products\"")))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.matches("(?s).*<button(?=[^>]*class=\"hero-motion-toggle\")(?=[^>]*type=\"button\")(?=[^>]*\\bhidden(?:[\\s=>]))[^>]*>.*"),
            "Pause control is a semantic button and remains hidden until enhancement succeeds");
    }

    @Test
    void chineseHomepageHasLocalizedAnimationControlsAndOriginalDescription() throws Exception {
        mockMvc.perform(get("/").param("lang", "zh"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("lang=\"zh-CN\"")))
            .andExpect(content().string(containsString("data-label-pause=\"暂停背景动画\"")))
            .andExpect(content().string(containsString("data-label-play=\"播放背景动画\"")))
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
            .andExpect(content().string(containsString("animation-play-state: paused")));
    }

    @Test
    void catalogueDoesNotLoadHomepageSpecificMotion() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/js/hero-motion.js"))))
            .andExpect(content().string(not(containsString("/css/hero-motion.css"))));
    }
}
