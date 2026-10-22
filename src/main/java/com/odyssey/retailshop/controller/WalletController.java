package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.WalletService;
import java.security.Principal;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class WalletController {
    private final WalletService wallets;
    public WalletController(WalletService wallets){this.wallets=wallets;}
    @GetMapping("/account/wallet") public String mine(Principal user,Model model){return page(user.getName(),false,model);}
    @GetMapping("/admin/wallet") public String admin(@RequestParam(defaultValue="") String email,Model model){return page(email,true,model);}
    private String page(String email,boolean admin,Model model){
        model.addAttribute("adminView",admin);model.addAttribute("databaseAvailable",wallets.available());
        model.addAttribute("searchEmail",email);model.addAttribute("adjustToken",UUID.randomUUID().toString());
        if(wallets.available() && !email.isBlank())try{
            var account=wallets.account(email);model.addAttribute("walletAccount",account);
            model.addAttribute("balance",wallets.balance(account.id()));model.addAttribute("entries",wallets.history(account.id()));
        }catch(IllegalArgumentException e){model.addAttribute("walletError",e.getMessage());}
        return "wallet";
    }
    @PostMapping("/admin/wallet/adjust") public String adjust(Principal user,@RequestParam String email,@RequestParam String direction,@RequestParam String amount,@RequestParam String note,@RequestParam String token,RedirectAttributes flash){
        try{wallets.adjust(user.getName(),email,direction,new BigDecimal(amount),note,token);flash.addFlashAttribute("walletMessage","Balance updated. / 余额已更新。");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("walletError",e instanceof NumberFormatException?"Invalid amount. / 金额格式不正确。":e.getMessage());}
        return "redirect:"+UriComponentsBuilder.fromPath("/admin/wallet").queryParam("email",email).build().encode().toUriString();
    }
}
