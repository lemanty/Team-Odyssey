package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.FavoriteService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FavoriteController {
    private final FavoriteService favorites;
    public FavoriteController(FavoriteService favorites){this.favorites=favorites;}
    @PostMapping("/favorites/{product}")
    public String toggle(@PathVariable long product,Principal user,@RequestParam(defaultValue="") String returnTo,RedirectAttributes flash){
        try{boolean saved=favorites.toggle(product,user.getName());flash.addFlashAttribute("favoriteMessage",saved?"Added to favourites. / 已加入收藏。":"Removed from favourites. / 已取消收藏。");}
        catch(IllegalArgumentException e){flash.addFlashAttribute("favoriteError",e.getMessage());}
        return "favorites".equals(returnTo)?"redirect:/account/favorites":"redirect:/products/"+product+"#favourites";
    }
    @GetMapping("/account/favorites")
    public String page(Principal user,Model model){
        model.addAttribute("favorites",favorites.favorites(user.getName()));
        model.addAttribute("notifications",favorites.notifications(user.getName()));
        model.addAttribute("unreadNotifications",favorites.unread(user.getName()));
        model.addAttribute("favoritesAvailable",favorites.available());
        return "favorites";
    }
    @PostMapping("/account/notifications/{notification}/read")
    public String read(@PathVariable long notification,Principal user){
        favorites.markRead(notification,user.getName());
        return "redirect:/account/favorites#notifications";
    }
    @PostMapping("/account/notifications/read-all")
    public String readAll(Principal user){
        favorites.markAllRead(user.getName());
        return "redirect:/account/favorites#notifications";
    }
}
