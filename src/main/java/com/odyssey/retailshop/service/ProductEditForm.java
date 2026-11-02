package com.odyssey.retailshop.service;

public class ProductEditForm {
 private Long id = null;
 public Long getId() { return id; }
 public void setId(Long value) { id=value; }
 private String revision = "";
 public String getRevision() { return revision; }
 public void setRevision(String value) { revision=value; }
 private String name = "";
 public String getName() { return name; }
 public void setName(String value) { name=value; }
 private String nameZh = "";
 public String getNameZh() { return nameZh; }
 public void setNameZh(String value) { nameZh=value; }
 private String description = "";
 public String getDescription() { return description; }
 public void setDescription(String value) { description=value; }
 private String descriptionZh = "";
 public String getDescriptionZh() { return descriptionZh; }
 public void setDescriptionZh(String value) { descriptionZh=value; }
 private String category = "Household";
 public String getCategory() { return category; }
 public void setCategory(String value) { category=value; }
 private String audience = "Home";
 public String getAudience() { return audience; }
 public void setAudience(String value) { audience=value; }
 private java.math.BigDecimal price = null;
 public java.math.BigDecimal getPrice() { return price; }
 public void setPrice(java.math.BigDecimal value) { price=value; }
 private Integer stock = 0;
 public Integer getStock() { return stock; }
 public void setStock(Integer value) { stock=value; }
 private Integer threshold = 5;
 public Integer getThreshold() { return threshold; }
 public void setThreshold(Integer value) { threshold=value; }
 private boolean published = false;
 public boolean getPublished() { return published; }
 public void setPublished(boolean value) { published=value; }
 private String image = "";
 public String getImage() { return image; }
 public void setImage(String value) { image=value; }
 private String secondImage = "";
 public String getSecondImage() { return secondImage; }
 public void setSecondImage(String value) { secondImage=value; }
 private java.util.List<Option> options = new java.util.ArrayList<>();
 public java.util.List<Option> getOptions() { return options; }
 public void setOptions(java.util.List<Option> value) { options=value; }
 public static class Option {
 private Long id = null;
 public Long getId() { return id; }
 public void setId(Long value) { id=value; }
 private String size = "";
 public String getSize() { return size; }
 public void setSize(String value) { size=value; }
 private String color = "";
 public String getColor() { return color; }
 public void setColor(String value) { color=value; }
 private Integer stock = 0;
 public Integer getStock() { return stock; }
 public void setStock(Integer value) { stock=value; }
 }
}
