package com.odyssey.retailshop.service;

import java.util.List;

/** Two-level catalogue taxonomy: top category (Men/Women/Household) and its subcategories. */
public final class CatalogCategories {
    public record Category(String key, String labelEn, String labelZh) {}
    public record Subcategory(String category, String key, String labelEn, String labelZh) {}

    private static final List<Category> CATEGORIES = List.of(
        new Category("Men", "Men", "男装"),
        new Category("Women", "Women", "女装"),
        new Category("Household", "Household", "家居")
    );

    private static final List<Subcategory> SUBCATEGORIES = List.of(
        new Subcategory("Men", "tops", "T-shirts", "T恤"),
        new Subcategory("Men", "shirts", "Shirts", "衬衫"),
        new Subcategory("Men", "outerwear", "Outerwear & jackets", "外套 / 夹克"),
        new Subcategory("Men", "knitwear", "Knitwear", "卫衣 / 针织 / 毛衣"),
        new Subcategory("Men", "trousers", "Trousers", "裤装"),
        new Subcategory("Women", "tops", "T-shirts", "T恤"),
        new Subcategory("Women", "shirts-blouses", "Shirts & blouses", "衬衫 / 上衣"),
        new Subcategory("Women", "outerwear", "Outerwear & jackets", "外套 / 夹克"),
        new Subcategory("Women", "knitwear", "Knitwear", "针织"),
        new Subcategory("Women", "trousers", "Trousers", "裤装"),
        new Subcategory("Women", "skirts-dresses", "Skirts & dresses", "裙装 / 连衣裙"),
        new Subcategory("Household", "living-room", "Living room", "客厅"),
        new Subcategory("Household", "bedroom", "Bedroom", "卧室"),
        new Subcategory("Household", "kitchen-dining", "Kitchen & dining", "厨房 / 餐厅"),
        new Subcategory("Household", "storage", "Storage", "收纳")
    );

    private CatalogCategories() {}

    public static List<Category> categories() { return CATEGORIES; }

    public static List<Subcategory> subcategories(String category) {
        return SUBCATEGORIES.stream().filter(s -> s.category().equals(category)).toList();
    }

    public static boolean valid(String category, String subcategory) {
        return CATEGORIES.stream().anyMatch(c -> c.key().equals(category))
            && SUBCATEGORIES.stream().anyMatch(s -> s.category().equals(category) && s.key().equals(subcategory));
    }
}