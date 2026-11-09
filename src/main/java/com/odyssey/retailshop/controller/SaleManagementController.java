package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.ProductManagementService;
import com.odyssey.retailshop.service.SaleService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SaleManagementController {
    private final SaleService sales;
    private final ProductManagementService products;

    public SaleManagementController(SaleService sales, ProductManagementService products) {
        this.sales = sales;
        this.products = products;
    }

    public record Draft(Long id, int revision, String title, String percent, String starts, String ends,
                        boolean enabled, Set<Long> productIds) {}

    @GetMapping("/admin/sales")
    public String manage(@RequestParam(required = false) Long edit, Model model) {
        if (!sales.available()) return page(model, null);
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        if (edit == null) {
            return page(model, new Draft(null, 0, "", "", now.toString(), now.plusDays(7).toString(), false, Set.of()));
        }
        try {
            var campaign = sales.campaign(edit);
            return page(model, new Draft(edit, campaign.revision(), campaign.title(), String.valueOf(campaign.percent()),
                campaign.starts().toString(), campaign.ends().toString(), campaign.enabled(), Set.copyOf(sales.productIds(edit))));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/admin/sales")
    public String save(@RequestParam(required = false) Long id, @RequestParam(defaultValue = "0") int revision,
                       @RequestParam String title, @RequestParam String percent, @RequestParam String starts,
                       @RequestParam String ends, @RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam(required = false) List<Long> productIds, Model model, RedirectAttributes flash) {
        if (!sales.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        Set<Long> selected = productIds == null ? Set.of() : Set.copyOf(productIds);
        try {
            sales.save(id, revision, title, Integer.parseInt(percent), LocalDateTime.parse(starts),
                LocalDateTime.parse(ends), enabled, selected);
            flash.addFlashAttribute("saleMessage", "Campaign saved. / 活动已保存。");
            return "redirect:/admin/sales";
        } catch (DateTimeParseException | NumberFormatException e) {
            model.addAttribute("saleError", "Enter a valid discount and dates. / 请填写有效的折扣和起止时间。");
        } catch (IllegalArgumentException e) {
            model.addAttribute("saleError", e.getMessage());
        }
        return page(model, new Draft(id, revision, title, percent, starts, ends, enabled, selected));
    }

    private String page(Model model, Draft draft) {
        model.addAttribute("draft", draft);
        if (draft != null) {
            model.addAttribute("campaigns", sales.campaigns());
            model.addAttribute("products", products.list("", "all"));
            model.addAttribute("now", LocalDateTime.now());
            model.addAttribute("saleTimezone", ZoneId.systemDefault().getId());
        }
        return "sale-management";
    }
}
