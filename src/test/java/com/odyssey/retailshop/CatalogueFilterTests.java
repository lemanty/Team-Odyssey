package com.odyssey.retailshop;
import com.odyssey.retailshop.controller.ProductController;
import com.odyssey.retailshop.service.*;
import com.odyssey.retailshop.model.Product;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.env.MockEnvironment;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CatalogueFilterTests {
 ProductService products=mock(ProductService.class);
 VariantService variants=mock(VariantService.class);
 ProductController controller=new ProductController(products,new MockEnvironment(),mock(StoreText.class),variants,mock(ReviewService.class),mock(SaleService.class, RETURNS_DEEP_STUBS));
 Product product(long id,int stock){return new Product(id,"Item "+id,"Men","",BigDecimal.TEN,"",stock,null,null);}
 ExtendedModelMap search(String size,String color,boolean stock,int page){var model=new ExtendedModelMap();controller.products(model,new MockHttpServletResponse(),"","","","","","default","",size,color,stock,page);return model;}
 @Test void sameVariantMustMatchBothOptionsAndStock(){
  when(products.getProducts()).thenReturn(List.of(product(1,4),product(2,0),product(3,5)));
  when(variants.all()).thenReturn(List.of(new VariantService.Variant(1,1,"M","Black",0),new VariantService.Variant(2,1,"L","Black",4),new VariantService.Variant(3,1,"M","White",3)));
  assertEquals(1,search("M","Black",false,1).get("totalProducts"));
  assertEquals(0,search("M","Black",true,1).get("totalProducts"));
  assertEquals(1,search("L","Black",true,1).get("totalProducts"));
  assertEquals(2,search("","",true,1).get("totalProducts"));
  assertEquals(0,search("XL","",false,1).get("totalProducts"));
 }
 @Test void stablePaginationAndOutOfRangePages(){
  var data=new ArrayList<Product>();for(long i=25;i>0;i--)data.add(product(i,1));
  when(products.getProducts()).thenReturn(data);when(variants.all()).thenReturn(List.of());
  var first=search("","",false,-1);assertEquals(1,first.get("page"));assertEquals(25,first.get("totalProducts"));
  var second=search("","",false,2);assertEquals(12,((List<?>)second.get("products")).size());assertEquals(13L,((Product)((List<?>)second.get("products")).get(0)).getId());
  var last=search("","",false,Integer.MAX_VALUE);assertEquals(3,last.get("page"));assertEquals(1,((List<?>)last.get("products")).size());
 }
}
