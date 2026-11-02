package com.odyssey.retailshop.controller;
import com.odyssey.retailshop.service.CouponService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class CouponController {
    private final CouponService coupons;
    public CouponController(CouponService coupons){this.coupons=coupons;}
    @ModelAttribute("couponTimezone") public String timezone(){return java.time.ZoneId.systemDefault().getId();}
    @GetMapping("/staff/coupons") public String manage(Model model){model.addAttribute("coupons",coupons.all());model.addAttribute("now",LocalDateTime.now());return "coupon-management";}
    @GetMapping("/account/coupons") public String mine(Principal user,Model model){model.addAttribute("offers",coupons.offers(user.getName(),new BigDecimal("9999999999.99")));return "coupons";}
    @PostMapping("/staff/coupons") public String create(Principal user,@RequestParam String code,@RequestParam String title,@RequestParam String kind,@RequestParam String value,@RequestParam String minimum,@RequestParam String starts,@RequestParam String ends,@RequestParam(defaultValue="false") boolean stack,RedirectAttributes flash){
        try{coupons.create(user.getName(),code,title,kind,new BigDecimal(value),new BigDecimal(minimum),LocalDateTime.parse(starts),LocalDateTime.parse(ends),stack);flash.addFlashAttribute("couponMessage","Coupon created. / 优惠券已创建。");}
        catch(java.time.format.DateTimeParseException|NumberFormatException e){flash.addFlashAttribute("couponError","Enter valid amounts and dates. / 请填写有效金额及日期。");saveDraft(flash,code,title,kind,value,minimum,starts,ends,stack);}
        catch(IllegalArgumentException e){flash.addFlashAttribute("couponError",e.getMessage());saveDraft(flash,code,title,kind,value,minimum,starts,ends,stack);}
        return "redirect:/staff/coupons";
    }
    private void saveDraft(RedirectAttributes flash,String code,String title,String kind,String value,String minimum,String starts,String ends,boolean stack){flash.addFlashAttribute("draft",java.util.Map.of("code",code,"title",title,"kind",kind,"value",value,"minimum",minimum,"starts",starts,"ends",ends,"stack",stack));}
    @PostMapping("/staff/coupons/{id}/status") public String toggle(Principal user,@PathVariable long id,@RequestParam boolean enabled,RedirectAttributes flash){try{coupons.toggle(user.getName(),id,enabled);flash.addFlashAttribute("couponMessage","Coupon status updated. / 优惠券状态已更新。");}catch(IllegalArgumentException e){flash.addFlashAttribute("couponError",e.getMessage());}return "redirect:/staff/coupons";}
}
