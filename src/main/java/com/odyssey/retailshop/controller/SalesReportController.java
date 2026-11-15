package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.SalesReportService;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SalesReportController {
    private final SalesReportService sales;
    public SalesReportController(SalesReportService sales) { this.sales = sales; }

    @GetMapping("/staff/reports/sales")
    public String report(@RequestParam(required=false) LocalDate start,
                         @RequestParam(required=false) LocalDate end, Model model) {
        if (end == null) end = LocalDate.now();
        if (start == null) start = end.minusDays(29);
        model.addAttribute("start",start);
        model.addAttribute("end",end);
        model.addAttribute("available",sales.available());
        model.addAttribute("invalidRange", start.isAfter(end));
        if (sales.available() && !start.isAfter(end)) model.addAttribute("report",sales.read(start,end));
        return "sales-report";
    }
}
