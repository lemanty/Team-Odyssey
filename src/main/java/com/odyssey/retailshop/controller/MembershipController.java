package com.odyssey.retailshop.controller;
import com.odyssey.retailshop.service.MembershipService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class MembershipController {
 private final MembershipService membership;
 public MembershipController(MembershipService membership) {this.membership=membership;}
 @GetMapping("/membership") public String membership(Principal user, Model model) {
  if(user!=null) model.addAttribute("memberLevel",membership.forAccount(user.getName()));
  return "membership";
 }
}
