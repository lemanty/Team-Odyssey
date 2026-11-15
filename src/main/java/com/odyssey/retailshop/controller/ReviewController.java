package com.odyssey.retailshop.controller;
import com.odyssey.retailshop.service.ReviewService;
import java.security.Principal;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews){this.reviews=reviews;}
    @PostMapping("/products/{id}/reviews")
    public String save(@PathVariable long id,Principal user,@RequestParam int rating,@RequestParam String body,@RequestParam(defaultValue="") String purchaseKey,@RequestParam(required=false) MultipartFile image,RedirectAttributes flash){
        try{reviews.save(id,user.getName(),rating,body,purchaseKey,image);flash.addFlashAttribute("reviewMessage","Your review has been saved. / 评论已保存。");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("reviewError",e.getMessage());flash.addFlashAttribute("reviewDraft",body);flash.addFlashAttribute("reviewRating",rating);flash.addFlashAttribute("reviewPurchase",purchaseKey);}
        return "redirect:/products/"+id+"#reviews";
    }
    @GetMapping("/review-images/{review}")
    public ResponseEntity<byte[]> image(@PathVariable long review){
        var image=reviews.image(review).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.type()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(image.bytes());
    }

    @PostMapping("/products/{id}/reviews/{review}/reply")
    public String reply(@PathVariable long id,@PathVariable long review,Principal user,@RequestParam String body,@RequestParam(defaultValue="0") int reviewPage,RedirectAttributes flash){
        try{reviews.reply(id,review,user.getName(),body);}catch(IllegalArgumentException e){flash.addFlashAttribute("reviewError",e.getMessage());}
        return "redirect:/products/"+id+"?reviewPage="+Math.max(0,Math.min(reviewPage,100000))+"#review-"+review;
    }
    @PostMapping("/products/{id}/reviews/{review}/edit")
    public String edit(@PathVariable long id,@PathVariable long review,Principal user,@RequestParam int rating,@RequestParam String body,RedirectAttributes flash){
        try{reviews.edit(id,review,user.getName(),rating,body);}catch(IllegalArgumentException e){flash.addFlashAttribute("reviewError",e.getMessage());}
        return "redirect:/products/"+id+"#review-"+review;
    }
    @PostMapping("/products/{id}/reviews/{review}/delete")
    public String delete(@PathVariable long id,@PathVariable long review,Principal user){reviews.delete(id,review,user.getName());return "redirect:/products/"+id+"#reviews";}
}
