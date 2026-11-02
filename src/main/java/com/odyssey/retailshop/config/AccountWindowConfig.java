package com.odyssey.retailshop.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.*;
import org.springframework.session.*;
import org.springframework.session.web.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

/** A public window selector chooses a separate HttpOnly cookie, never an identity. */
@Configuration
@EnableScheduling
public class AccountWindowConfig {
    private final Map<String,Session> sessions = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay=60000)
    public void expireSessions() { sessions.entrySet().removeIf(entry -> entry.getValue().isExpired()); }

    public static String window(HttpServletRequest request) {
        // Read only the query string: multipart bodies must not be consumed before security.
        String query=request.getQueryString();
        if(query==null) return null;
        var values=UriComponentsBuilder.newInstance().query(query).build().getQueryParams().get("window");
        if(values==null || values.size()!=1) return null;
        String value=values.get(0);
        return value!=null && value.matches("[a-f0-9]{32}") ? value : null;
    }
    public static String url(String url,String window) {
        if(window==null || url==null || !url.startsWith("/") || url.startsWith("//")) return url;
        if(url.startsWith("/css/") || url.startsWith("/js/") || url.startsWith("/images/")) return url;
        return UriComponentsBuilder.fromUriString(url).replaceQueryParam("window",window).build(true).toUriString();
    }
    // Keep Spring Security's CSRF form processor and add the public selector to generated links.
    @Bean
    static org.springframework.beans.factory.config.BeanPostProcessor windowLinks() {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(!name.equals("requestDataValueProcessor") || !(bean instanceof org.springframework.web.servlet.support.RequestDataValueProcessor delegate)) return bean;
                return new org.springframework.web.servlet.support.RequestDataValueProcessor() {
                    public String processAction(HttpServletRequest request,String action,String method) {
                        return url(delegate.processAction(request,action,method),window(request));
                    }
                    public String processUrl(HttpServletRequest request,String value) {
                        return url(delegate.processUrl(request,value),window(request));
                    }
                    public String processFormFieldValue(HttpServletRequest request,String name,String value,String type) {
                        return delegate.processFormFieldValue(request,name,value,type);
                    }
                    public Map<String,String> getExtraHiddenFields(HttpServletRequest request) {
                        var fields=new HashMap<String,String>();
                        var original=delegate.getExtraHiddenFields(request);
                        if(original!=null) fields.putAll(original);
                        String selected=window(request);
                        if(selected!=null) fields.put("window",selected);
                        return fields;
                    }
                };
            }
        };
    }
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> accountWindowFilter() {
        var repository=new MapSessionRepository(sessions);
        var sessionFilter=new SessionRepositoryFilter<>(repository);
        sessionFilter.setHttpSessionIdResolver(new HttpSessionIdResolver() {
            private String name(HttpServletRequest request) { return "ODYSSEY_"+window(request); }
            public List<String> resolveSessionIds(HttpServletRequest request) {
                if(request.getCookies()!=null) for(Cookie cookie:request.getCookies())
                    if(cookie.getName().equals(name(request))) return List.of(cookie.getValue());
                return List.of();
            }
            public void setSessionId(HttpServletRequest request,HttpServletResponse response,String id) { cookie(request,response,id,-1); }
            public void expireSession(HttpServletRequest request,HttpServletResponse response) { cookie(request,response,"",0); }
            private void cookie(HttpServletRequest request,HttpServletResponse response,String id,long age) {
                response.addHeader("Set-Cookie",ResponseCookie.from(name(request),id).path("/").httpOnly(true)
                    .sameSite("Lax").secure(request.isSecure()).maxAge(age).build().toString());
            }
        });
        var filter=new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
                String selected=window(request);
                if(selected==null) { chain.doFilter(request,response); return; }
                // Spring Security may issue absolute login redirects; preserve only this origin.
                String origin=request.getScheme()+"://"+request.getServerName()+
                    ((request.getServerPort()==80 && request.getScheme().equals("http")) || (request.getServerPort()==443 && request.getScheme().equals("https")) ? "" : ":"+request.getServerPort());
                // Thymeleaf links and Spring redirects retain the selected window server-side.
                sessionFilter.doFilter(request,response,(sessionRequest,sessionResponse) -> {
                var scopedResponse=new HttpServletResponseWrapper((HttpServletResponse)sessionResponse) {
                    private String scoped(String value) {
                        if(value!=null && value.startsWith(origin+"/")) return origin+url(value.substring(origin.length()),selected);
                        return url(value,selected);
                    }
                    @Override public String encodeURL(String value) { return scoped(value); }
                    @Override public String encodeRedirectURL(String value) { return scoped(value); }
                    @Override public void sendRedirect(String value) throws IOException { super.sendRedirect(scoped(value)); }
                    @Override public void setHeader(String name,String value) { super.setHeader(name,"Location".equalsIgnoreCase(name)?scoped(value):value); }
                };
                chain.doFilter(sessionRequest,scopedResponse);
                });
            }
        };
        var registration=new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Integer.MIN_VALUE+100);
        return registration;
    }
}
