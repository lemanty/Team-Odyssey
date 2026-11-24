package com.odyssey.retailshop.controller;
import com.odyssey.retailshop.service.AddressService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class AddressController {
 private final AddressService addresses;
 public AddressController(AddressService addresses){this.addresses=addresses;}
 @GetMapping("/account/addresses") public String page(Principal user,@RequestParam(required=false) Long edit,Model model){
  populatePage(user,model);
  if(edit!=null)model.addAttribute("editing",addresses.get(user.getName(),edit));return "addresses";
 }
 @PostMapping("/account/addresses") public String save(Principal user,@RequestParam(required=false) Long id,@RequestParam String recipient,@RequestParam String phone,@RequestParam String addressLine,@RequestParam(defaultValue="false") boolean makeDefault,Model model,RedirectAttributes flash){
  try{addresses.save(user.getName(),id,recipient,phone,addressLine,makeDefault);}
  catch(IllegalArgumentException e){model.addAttribute("addressError",e.getMessage());populatePage(user,model);model.addAttribute("editing",new AddressService.Address(id==null?0:id,recipient,phone,addressLine,makeDefault));return "addresses";}
  flash.addFlashAttribute("addressMessage","Address saved. / 地址已保存。");return "redirect:/account/addresses";
 }
 private void populatePage(Principal user, Model model) {
  var saved = addresses.list(user.getName());
  model.addAttribute("addresses", saved);
  model.addAttribute("databaseAvailable", addresses.available());
  saved.stream().filter(AddressService.Address::isDefault).findFirst()
      .ifPresent(address -> model.addAttribute("defaultAddressId", address.id()));
 }
 @PostMapping("/account/addresses/{id}/default") public String makeDefault(Principal user,@PathVariable long id){addresses.setDefault(user.getName(),id);return "redirect:/account/addresses";}
 @PostMapping("/account/addresses/{id}/delete") public String delete(Principal user,@PathVariable long id){addresses.delete(user.getName(),id);return "redirect:/account/addresses";}
}
