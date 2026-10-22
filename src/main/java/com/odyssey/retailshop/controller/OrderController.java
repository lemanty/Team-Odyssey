package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.*;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderController {
    private final OrderService orders;
    private final CouponService coupons;
    private final OrderWorkspaceService workspace;
    private final ReturnService returns;
    private final CartService cart;
    private final MembershipService membership;
    private final AddressService addresses;
    private final WalletService wallets;
    private final OrderPaymentService payments;
    public OrderController(OrderService orders, CartService cart, MembershipService membership, AddressService addresses, WalletService wallets, OrderPaymentService payments, OrderWorkspaceService workspace, ReturnService returns, CouponService coupons) { this.coupons=coupons; this.returns=returns; this.workspace=workspace; this.wallets=wallets; this.payments=payments; this.addresses=addresses; this.orders=orders; this.cart=cart; this.membership=membership; }
    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model, Principal user) {
        synchronized(session) {
            if (session.getAttribute("checkoutToken") == null) session.setAttribute("checkoutToken",UUID.randomUUID().toString());
            model.addAttribute("checkoutToken",session.getAttribute("checkoutToken"));
            var bag=cart.view(session);
            model.addAttribute("bag",bag);
            var memberQuote=membership.quote(user.getName(),bag.total());
            String code=model.containsAttribute("selectedCoupon")?(String)model.getAttribute("selectedCoupon"):"";
            try {
                var quote=coupons.quote(user.getName(),memberQuote,code,false);
                model.addAttribute("pricing",quote.membership());
                model.addAttribute("couponPricing",quote.coupon()==null?null:new CouponService.Snapshot(quote.coupon().code(),quote.coupon().title(),quote.coupon().stack(),quote.discount()));
                model.addAttribute("selectedCoupon",quote.coupon()==null?"":quote.coupon().code());
            }catch(IllegalArgumentException e){model.addAttribute("pricing",memberQuote);model.addAttribute("selectedCoupon","");model.addAttribute("orderError",e.getMessage());}
            model.addAttribute("couponOffers",coupons.offers(user.getName(),bag.total()));
        }
        model.addAttribute("walletBalance",wallets.available()?wallets.balance(wallets.account(user.getName()).id()):java.math.BigDecimal.ZERO);
        model.addAttribute("addresses",addresses.list(user.getName()));
        return "checkout";
    }
    @PostMapping("/checkout/coupon")
    public String previewCoupon(@RequestParam(defaultValue="") String couponCode,@RequestParam(defaultValue="DELIVERY") String fulfillment,@RequestParam(defaultValue="") String address,@RequestParam(required=false) Long addressId,@RequestParam(defaultValue="WALLET") String paymentMethod,RedirectAttributes flash){
        flash.addFlashAttribute("selectedCoupon",couponCode);flash.addFlashAttribute("selectedFulfillment",fulfillment);
        flash.addFlashAttribute("checkoutAddress",address);flash.addFlashAttribute("selectedAddressId",addressId==null?0L:addressId);flash.addFlashAttribute("selectedPayment",paymentMethod);
        return "redirect:/checkout";
    }
    @PostMapping("/orders")
    public String place(@RequestParam String token, @RequestParam String fulfillment, @RequestParam(defaultValue="") String address, @RequestParam(required=false) Long addressId, @RequestParam(defaultValue="OFFLINE") String paymentMethod, @RequestParam(defaultValue="") String couponCode, Principal user, HttpSession session, RedirectAttributes flash) {
        synchronized(session) {
            // Old tokens can retry the existing order, but cannot create a new order.
            String previous=(String)session.getAttribute("lastCheckoutToken");
            if (!token.equals(session.getAttribute("checkoutToken")) && !token.equals(previous)) {
                flash.addFlashAttribute("orderError","Please reopen checkout. / 请重新打开结算页。"); return "redirect:/checkout";
            }
            if (token.equals(previous)) return "redirect:/orders";
            try {
                String destination="DELIVERY".equals(fulfillment)&&addressId!=null ? addresses.get(user.getName(),addressId).formatted() : address;
                String id=orders.placeFromCart(user.getName(),token,fulfillment,destination,paymentMethod,couponCode);
                session.removeAttribute("checkoutToken");
                session.setAttribute("lastCheckoutToken",token); session.setAttribute("lastCheckoutId",id);
                flash.addFlashAttribute("orderMessage", "WALLET".equals(paymentMethod)?"Order placed and paid from your wallet. / 订单提交成功，已使用储值余额支付。":"Order placed; offline payment is not yet recorded. / 订单提交成功，线下付款尚未记录。");
                return "redirect:/orders";
            } catch (IllegalArgumentException e) {
                flash.addFlashAttribute("orderError",e.getMessage());
                flash.addFlashAttribute("selectedCoupon",couponCode);
                flash.addFlashAttribute("selectedPayment",paymentMethod);
                flash.addFlashAttribute("selectedFulfillment",fulfillment);
                flash.addFlashAttribute("checkoutAddress",address);
                flash.addFlashAttribute("selectedAddressId",addressId==null?0L:addressId);
                return "redirect:/checkout";
            }
        }
    }
    @GetMapping("/orders")
    public String mine(Principal user, Model model) { return listing(user,model,false); }
    @GetMapping("/staff/orders")
    public String staff(Principal user, Model model,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to,@RequestParam(defaultValue="0") int page) {
        page=Math.max(0,Math.min(page,100000));
        try{model.addAttribute("result",workspace.search(q,status,from,to,page));}
        catch(IllegalArgumentException e){model.addAttribute("orderError",e.getMessage());model.addAttribute("result",workspace.search("","ALL","","",0));}
        model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("from",from);model.addAttribute("to",to);model.addAttribute("page",page);return "staff-orders";
    }
    private String listing(Principal user, Model model, boolean staff) {
        model.addAttribute("orderPreviews",orders.previews(user.getName(),staff));
        model.addAttribute("orders",orders.list(user.getName(),staff)); model.addAttribute("staffView",staff); return "orders";
    }
    @GetMapping("/orders/{id}")
    public String detail(@PathVariable String id, Principal user, Model model) { return detail(id,user,model,false); }
    @GetMapping("/staff/orders/{id}")
    public String staffDetail(@PathVariable String id, Principal user, Model model) { return detail(id,user,model,true); }
    private String detail(String id, Principal user, Model model, boolean staff) {
        var order=orders.get(id,user.getName(),staff);
        model.addAttribute("order",order);
        model.addAttribute("payment",payments.snapshot(id));
        model.addAttribute("couponPricing",coupons.snapshot(id));
        model.addAttribute("returnRefunded",returns.refunded(id));
        model.addAttribute("pricing",membership.snapshot(id,order.total()));
        model.addAttribute("items",orders.items(id)); model.addAttribute("staffView",staff); return "order-detail";
    }
    @PostMapping("/orders/{id}/edit")
    public String edit(@PathVariable String id, @RequestParam String fulfillment, @RequestParam(defaultValue="") String address, Principal user, RedirectAttributes flash) {
        try { orders.edit(id,user.getName(),fulfillment,address); flash.addFlashAttribute("orderMessage","Order updated. / 订单已更新。"); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable String id, Principal user, RedirectAttributes flash) {
        try { orders.cancel(id,user.getName()); flash.addFlashAttribute("orderMessage","Order cancelled; stock released and any wallet payment refunded. / 订单已取消，库存已释放；如使用储值支付，款项已退回余额。"); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @PostMapping("/orders/{id}/confirm-receipt")
    public String confirmReceipt(@PathVariable String id,Principal user,RedirectAttributes flash) {
        try { orders.confirmReceipt(id,user.getName()); flash.addFlashAttribute("orderMessage","Receipt confirmed. Order completed. / 已确认收货，订单已完成。"); }
        catch(IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @PostMapping("/staff/orders/{id}/status")
    public String advance(@PathVariable String id, @RequestParam String next, RedirectAttributes flash) {
        try { orders.advance(id,next); flash.addFlashAttribute("orderMessage","Status updated. / 状态已更新。"); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/staff/orders/"+id;
    }
}
