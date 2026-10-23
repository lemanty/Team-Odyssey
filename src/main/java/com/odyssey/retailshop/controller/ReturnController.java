package com.odyssey.retailshop.controller;
import com.odyssey.retailshop.service.*;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.*;
@Controller
public class ReturnController {
    private final ReturnService returns;private final OrderService orders;private final ProductService products;
    public ReturnController(ReturnService returns,OrderService orders,ProductService products){this.returns=returns;this.orders=orders;this.products=products;}
    @GetMapping("/orders/returns") public String mine(Principal user,Model model){return listing(user,model,false);}
    @GetMapping("/staff/returns") public String staff(Principal user,Model model){return listing(user,model,true);}
    private String listing(Principal user,Model model,boolean staff){model.addAttribute("requests",returns.list(user.getName(),staff));if(!staff){var images=new java.util.HashMap<Long,String>();for(var p:products.getProducts())if(p.getGarmentImage()!=null)images.put(p.getId(),p.getGarmentImage());model.addAttribute("returnImages",images);}model.addAttribute("staffView",staff);return "returns";}
    @GetMapping("/orders/{id}/return") public String form(@PathVariable String id,Principal user,Model model){model.addAttribute("order",orders.get(id,user.getName(),false));model.addAttribute("items",orders.items(id));model.addAttribute("token",UUID.randomUUID().toString());return "return-form";}
    @PostMapping("/orders/{id}/return") public String create(@PathVariable String id,@RequestParam long itemKey,@RequestParam int quantity,@RequestParam String reason,@RequestParam String token,@RequestParam(required=false) MultipartFile image,Principal user,RedirectAttributes flash){
        try{return "redirect:/orders/returns/"+returns.create(id,user.getName(),itemKey,quantity,reason,token,SupportImage.from(image));}
        catch(IllegalArgumentException e){flash.addFlashAttribute("orderError",e.getMessage());flash.addFlashAttribute("draftReason",reason);flash.addFlashAttribute("draftQuantity",quantity);flash.addFlashAttribute("draftItem",itemKey);return "redirect:/orders/"+id+"/return";}
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String tooLarge(RedirectAttributes flash){flash.addFlashAttribute("orderError","The image is too large (maximum 5 MB). / 图片太大（最大5MB），请从订单重新提交。");return "redirect:/orders/returns";}
    @GetMapping("/orders/returns/{id}") public String detail(@PathVariable String id,Principal user,Model model){return detail(id,user,model,false);}
    @GetMapping("/staff/returns/{id}") public String staffDetail(@PathVariable String id,Principal user,Model model){return detail(id,user,model,true);}
    private String detail(String id,Principal user,Model model,boolean staff){var request=returns.get(id,user.getName(),staff);model.addAttribute("request",request);model.addAttribute("events",returns.events(id));model.addAttribute("staffView",staff);return "return-detail";}
    @GetMapping("/orders/returns/{id}/image") public ResponseEntity<byte[]> image(@PathVariable String id,Principal user){return image(id,user,false);}
    @GetMapping("/staff/returns/{id}/image") public ResponseEntity<byte[]> staffImage(@PathVariable String id,Principal user){return image(id,user,true);}
    private ResponseEntity<byte[]> image(String id,Principal user,boolean staff){var img=returns.image(id,user.getName(),staff);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(img.type())).body(img.bytes());}
    @PostMapping("/staff/returns/{id}") public String process(@PathVariable String id,@RequestParam String next,@RequestParam String note,@RequestParam(defaultValue="false") boolean restock,@RequestParam(defaultValue="false") boolean refund,@RequestParam(defaultValue="false") boolean offlineConfirmed,Principal user,RedirectAttributes flash){
        try{returns.process(id,user.getName(),next,note,restock,refund,offlineConfirmed);flash.addFlashAttribute("orderMessage","Request updated. / 售后申请已更新。");}catch(IllegalArgumentException e){flash.addFlashAttribute("orderError",e.getMessage());}return "redirect:/staff/returns/"+id;
    }
}
