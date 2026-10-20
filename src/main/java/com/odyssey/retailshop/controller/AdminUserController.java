package com.odyssey.retailshop.controller;
import com.odyssey.retailshop.service.AdminUserService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class AdminUserController {
 private final AdminUserService users;
 public AdminUserController(AdminUserService users){this.users=users;}
 @GetMapping("/admin/users") public String list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,Principal actor,Model model){
  int current=Math.max(0,Math.min(page,100000));String search=q.strip();if(search.length()>254)search=search.substring(0,254);
  var accounts=users.available()?users.list(search,current):java.util.List.<AdminUserService.Account>of();
  model.addAttribute("accounts",accounts.stream().limit(50).toList());model.addAttribute("hasNext",accounts.size()>50);model.addAttribute("page",current);model.addAttribute("q",search);model.addAttribute("actorEmail",actor.getName());model.addAttribute("databaseAvailable",users.available());return "admin-users";
 }
 @PostMapping("/admin/users/{id}/role") public String change(@PathVariable long id,@RequestParam String role,@RequestParam String expected,Principal actor,RedirectAttributes flash){
  try{users.change(actor.getName(),id,expected,role);flash.addFlashAttribute("roleMessage","Role saved. Effective on the next request. / 权限已保存，下次请求生效。");}catch(IllegalArgumentException e){flash.addFlashAttribute("roleError",e.getMessage());}
  return "redirect:/admin/users";
 }
 @PostMapping("/admin/users/{id}/delete") public String delete(@PathVariable long id,@RequestParam String expected,Principal actor,RedirectAttributes flash){
  try{users.delete(actor.getName(),id,expected);flash.addFlashAttribute("roleMessage","Account deleted; historical records retained. / 账号已删除，历史记录已保留。");}catch(IllegalArgumentException e){flash.addFlashAttribute("roleError",e.getMessage());}
  return "redirect:/admin/users";
 }
}
