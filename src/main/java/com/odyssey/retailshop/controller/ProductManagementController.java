package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.*;

@Controller
public class ProductManagementController {
    private final ProductManagementService products;
    public ProductManagementController(ProductManagementService products){this.products=products;}
    @GetMapping("/staff/products")
    public String list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String status,Model model){
        model.addAttribute("available",products.available());model.addAttribute("q",q);model.addAttribute("status",status);
        model.addAttribute("items",products.available()?products.list(q,status):java.util.List.of());return "manage-products";
    }
    @GetMapping("/staff/products/new")
    public String create(Model model){model.addAttribute("form",new ProductEditForm());model.addAttribute("available",products.available());return "edit-product";}
    @GetMapping("/staff/products/{id}/edit")
    public String edit(@PathVariable long id,Model model){model.addAttribute("form",products.edit(id));model.addAttribute("available",true);return "edit-product";}
    @PostMapping("/staff/products/save")
    public String save(@ModelAttribute("form") ProductEditForm form,BindingResult result,
            @RequestParam(required=false) MultipartFile mainPhoto,@RequestParam(required=false) MultipartFile extraPhoto,Model model,RedirectAttributes flash){
        model.addAttribute("available",products.available());
        if(result.hasErrors()){model.addAttribute("error","Check numeric fields and select images again. / 请检查价格、库存等数字字段，并重新选择上传图片。");return "edit-product";}
        try { long id=products.save(form,mainPhoto,extraPhoto);flash.addFlashAttribute("success","Product saved. / 商品已保存。");return "redirect:/staff/products/"+id+"/edit"; }
        catch(IllegalArgumentException ex){model.addAttribute("error",ex.getMessage());return "edit-product";}
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String large(RedirectAttributes flash){flash.addFlashAttribute("error","Image upload too large. Use PNG/JPEG up to 5 MB each. / 图片过大，每张 PNG/JPEG 不得超过 5 MB，请重新打开编辑页面。");return "redirect:/staff/products";}
    @GetMapping("/catalogue-images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id){return products.image(id).map(p->ResponseEntity.ok().contentType(MediaType.parseMediaType(p.type())).header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30))).body(p.bytes())).orElseGet(()->ResponseEntity.notFound().build());}
}
