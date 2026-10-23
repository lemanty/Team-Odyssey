package com.odyssey.retailshop.controller;
import com.odyssey.retailshop.service.ReviewService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews){this.reviews=reviews;}
    @PostMapping("/products/{id}/reviews")
    public String save(@PathVariable long id,Principal user,@RequestParam int rating,@RequestParam String body,@RequestParam(defaultValue="") String purchaseKey,RedirectAttributes flash){
        try{reviews.save(id,user.getName(),rating,body,purchaseKey);flash.addFlashAttribute("reviewMessage","Your review has been saved. / 评论已保存。");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("reviewError",e.getMessage());flash.addFlashAttribute("reviewDraft",body);flash.addFlashAttribute("reviewRating",rating);flash.addFlashAttribute("reviewPurchase",purchaseKey);}
        return "redirect:/products/"+id+"#reviews";
    }
    @PostMapping("/products/{id}/reviews/delete")
    public String delete(@PathVariable long id,Principal user,RedirectAttributes flash){reviews.delete(id,user.getName());flash.addFlashAttribute("reviewMessage","Review deleted. / 评论已删除。");return "redirect:/products/"+id+"#reviews";}
}
