package com.odyssey.retailshop.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Display translations for the sample catalogue; original data and order snapshots stay unchanged. */
@Component("storeText")
public class StoreText {
    private final Map<String,String> chinese = new HashMap<>();
    public StoreText() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(new ClassPathResource("store-zh.tsv").getInputStream(),StandardCharsets.UTF_8))) {
            for (String line; (line=reader.readLine()) != null;) {
                String[] pair=line.split("\t",2);
                if (pair.length==2) chinese.put(pair[0],pair[1]);
            }
        }
    }
    public String text(String source, boolean zh) {
        if (source == null) return "";
        // Existing order feedback carries both languages separated by this delimiter.
        int separator=source.indexOf(" / ");
        if (separator>=0 && source.substring(separator+3).codePoints().anyMatch(c -> Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))
            return zh ? source.substring(separator+3) : source.substring(0,separator);
        return zh ? chinese.getOrDefault(source,source) : source;
    }
}
