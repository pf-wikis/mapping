package io.github.pfwikis;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Helper {

    public static String buildQuery(String base, String... params) {
        StringBuilder sb = new StringBuilder(base);
        sb.append("?");
        for(int i=0;i<params.length;i+=2) {
            if(i>0) {
                sb.append("&");
            }
            sb
                .append(URLEncoder.encode(params[i], StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(params[i+1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public static String handleName(String name, String pageName) {
        if(StringUtils.isBlank(name)) name = pageName;
        name = name.replaceAll(" +\\(.*", "");
        return name;
    }

    public static <T> T read(String url, JavaType type) throws MalformedURLException, IOException {
        var tree = Jackson.get().readTree(new URL(url));
        stripHTML(tree);
        return Jackson.get().treeToValue(tree, type);
    }

    private static void stripHTML(JsonNode n) {
        n.elements().forEachRemaining(c->stripHTML(c));
        if(n instanceof ObjectNode obj) {
            n.fieldNames().forEachRemaining(f-> {
                var v = n.get(f);
                if(v.isTextual()) {
                    obj.put(f, stripHTML(v.textValue()));
                }
            });
        }
    }

    private static String stripHTML(String textValue) {
        return Jsoup.parseBodyFragment(textValue).text();
    }

    private static String[] REMOVED_SELECTORS = {
		"table",
		"div",
		"script",
		"input",
		"style",
		"ul.gallery",
		".mw-editsection",
		"sup.reference",
		".reference-group",
		"ol.references",
		".error",
		".nomobile",
		".noprint",
		".noexcerpt",
		".sortkey",
		"#spoilerWarning"
	};
	public static String downloadText(String pageName) throws IOException {
		var resp = Jackson.get().readTree(URI.create(
			buildQuery("https://pathfinderwiki.com/w/api.php",
				"format", "json",
				"utf8", "1",
				"formatversion", "2",
				"action", "parse",
				"disablelimitreport", "true",
				"disableeditsection", "true",
				"disabletoc", "true",
				"page", pageName
		)).toURL());
		var parsed = resp.at("/parse/text").asText();
		if(StringUtils.isBlank(parsed)) {
			return null;
		}
		
		var doc = Jsoup.parseBodyFragment(parsed, "https://pathfinderwiki.com");
		var output = doc.select(".mw-parser-output").first();
		doc.body().children().remove();
		output.children().forEach(doc.body()::appendChild);
		
		for(var select:REMOVED_SELECTORS) {
			doc.select(select).remove();
		}
		//make URLs absolute
		doc.getElementsByAttribute("href").forEach(e->e.attr("href", e.absUrl("href")));
		//remove red links
		doc.getElementsByTag("a").forEach(a->{
			if(a.classNames().contains("new")) {
				a.childNodes().forEach(c->a.before(c));
				a.remove();
			}
		});
		//open links in new tab
		doc.getElementsByTag("a").attr("target", "_blank");
		
		var raw = doc.body().html();
		
		//cut off sections
		raw = raw.replaceAll("(?s)<h\\d.*", "");
		doc = Jsoup.parseBodyFragment(raw);
		return doc.body().html();
	}
}
