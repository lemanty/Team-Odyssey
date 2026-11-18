package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.OperationsReportService;
import com.odyssey.retailshop.service.OperationsReportService.Table;
import com.odyssey.retailshop.service.SalesReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/staff/reports/operations")
public class OperationsReportController {
    private final OperationsReportService operations;
    private final SalesReportService sales;

    public OperationsReportController(OperationsReportService operations, SalesReportService sales) {
        this.operations = operations;
        this.sales = sales;
    }

    public record Filters(String section, LocalDate start, LocalDate end, String status,
                          String fulfillment, String q, String stock, String sort, Long product) {}

    private Filters filters(String section, String start, String end, String status,
                            String fulfillment, String q, String stock, String sort, Long product) {
        if (!Set.of("sales","products","inventory","orders","returns").contains(section)
            || !Set.of("all","low","out").contains(stock)
            || !Set.of("top","slow").contains(sort)
            || !Set.of("ALL","PENDING","PREPARING","SHIPPED","READY","COMPLETED","CANCELLED").contains(status)
            || !Set.of("ALL","DELIVERY","COLLECTION").contains(fulfillment)) {
            throw new IllegalArgumentException("Invalid filters / 筛选条件无效");
        }
        try {
            LocalDate last = end.isBlank() ? LocalDate.now() : LocalDate.parse(end);
            LocalDate first = start.isBlank() ? last.minusDays(29) : LocalDate.parse(start);
            if (first.isAfter(last) || ChronoUnit.DAYS.between(first,last)>365
                || first.isBefore(LocalDate.of(2000,1,1)) || last.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Select a past or current period of up to 366 days / 请选择截至今天、不超过366天的日期范围");
            }
            return new Filters(section,first,last,status,fulfillment,q,stock,sort,product);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("Enter valid dates / 请输入有效日期");
        }
    }

    private Table table(Filters f) {
        return switch (f.section()) {
            case "products" -> f.product()==null ? operations.products(f.start(),f.end(),f.sort())
                : operations.productOrders(f.start(),f.end(),f.product());
            case "inventory" -> operations.inventory(f.stock());
            case "orders" -> operations.orders(f.start(),f.end(),f.status(),f.fulfillment(),f.q());
            case "returns" -> operations.returns(f.start(),f.end(),f.status());
            default -> operations.trend(f.start(),f.end());
        };
    }

    @GetMapping
    public String report(@RequestParam(defaultValue="sales") String section,
            @RequestParam(defaultValue="") String start, @RequestParam(defaultValue="") String end,
            @RequestParam(defaultValue="ALL") String status, @RequestParam(defaultValue="ALL") String fulfillment,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="all") String stock,
            @RequestParam(defaultValue="top") String sort, @RequestParam(required=false) Long product,
            Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        model.addAttribute("available", operations.available());
        try {
            Filters f = filters(section,start,end,status,fulfillment,q,stock,sort,product);
            model.addAttribute("filters",f);
            if (operations.available()) {
                Table table = table(f);
                model.addAttribute("table",table);
                if (section.equals("orders")) {
                    model.addAttribute("orderCount",table.rows().size());
                    model.addAttribute("orderTotal",table.rows().stream().map(row -> new BigDecimal(row.get(4)))
                        .reduce(BigDecimal.ZERO,BigDecimal::add));
                }
                if (section.equals("sales")) {
                    long days = ChronoUnit.DAYS.between(f.start(),f.end())+1;
                    var current = sales.read(f.start(),f.end());
                    var previous = sales.read(f.start().minusDays(days),f.start().minusDays(1));
                    model.addAttribute("current",current);
                    model.addAttribute("previous",previous);
                    model.addAttribute("comparisonStart",f.start().minusDays(days));
                    model.addAttribute("comparisonEnd",f.start().minusDays(1));
                    model.addAttribute("salesDifference",current.sales().subtract(previous.sales()));
                    model.addAttribute("salesPercent",previous.sales().signum()==0 ? null :
                        current.sales().subtract(previous.sales()).multiply(BigDecimal.valueOf(100))
                            .divide(previous.sales(),2,java.math.RoundingMode.HALF_UP));
                    model.addAttribute("averageOrder",current.orders()==0 ? BigDecimal.ZERO :
                        current.sales().divide(BigDecimal.valueOf(current.orders()),2,java.math.RoundingMode.HALF_UP));
                    model.addAttribute("categories",operations.categories(f.start(),f.end()));
                    model.addAttribute("trendMax",table.rows().stream().map(row -> new BigDecimal(row.get(2)))
                        .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).max(BigDecimal.ONE));
                }
                if (section.equals("returns")) {
                    model.addAttribute("reasons",operations.reasons(f.start(),f.end()));
                    model.addAttribute("averageReturnHours",table.rows().stream()
                        .filter(row -> Set.of("COMPLETED","REJECTED").contains(row.get(2)))
                        .mapToLong(row -> Long.parseLong(row.get(7))).average().orElse(0));
                    model.addAttribute("refundTotal",table.rows().stream().filter(row -> row.get(2).equals("COMPLETED"))
                        .map(row -> new BigDecimal(row.get(4))).reduce(BigDecimal.ZERO,BigDecimal::add));
                    model.addAttribute("pendingCount",table.rows().stream()
                        .filter(row -> Set.of("REQUESTED","APPROVED","RECEIVED").contains(row.get(2))).count());
                }
            }
        } catch (IllegalArgumentException ex) {
            response.setStatus(400);
            model.addAttribute("error",ex.getMessage());
        }
        return "operations-report";
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(defaultValue="sales") String section,
            @RequestParam(defaultValue="") String start, @RequestParam(defaultValue="") String end,
            @RequestParam(defaultValue="ALL") String status, @RequestParam(defaultValue="ALL") String fulfillment,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="all") String stock,
            @RequestParam(defaultValue="top") String sort, @RequestParam(required=false) Long product) {
        try {
            Filters f = filters(section,start,end,status,fulfillment,q,stock,sort,product);
            if (!operations.available()) return ResponseEntity.status(503).body("Database unavailable");
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=odyssey-"+section+"-"+f.start()+"-"+f.end()+".csv")
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(csv(table(f)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    public static String csv(Table table) {
        StringBuilder result = new StringBuilder("\uFEFF");
        List<List<String>> rows = new ArrayList<>();
        rows.add(table.columns());
        rows.addAll(table.rows());
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String value : row) {
                String safe = value == null ? "" : value;
                String trimmed = safe.stripLeading();
                if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0) safe = "'" + safe;
                cells.add("\"" + safe.replace("\"","\"\"") + "\"");
            }
            result.append(String.join(",",cells)).append("\r\n");
        }
        return result.toString();
    }
}
