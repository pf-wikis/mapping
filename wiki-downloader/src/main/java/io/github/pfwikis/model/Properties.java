package io.github.pfwikis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.github.pfwikis.Helper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({"label", "link", "type", "capital", "size", "filterMinzoom", "articleLength", "text"})
public class Properties {

    private String label;
    private String link;
    private Boolean capital;
    private Integer size;
    private String type;
    private String text;
    private int articleLength;

    public Properties(City city) {
        label = Helper.handleName(city.getName(), city.getPageName());
        link = "https://pathfinderwiki.com/wiki/" + city.getPageName().replace(' ', '_');
        capital = city.isCapital();
        text = city.getText();
        
        articleLength = city.getArticleLength()/100*100;
    }

	public Properties(LoI loi) {
		label = Helper.handleName(loi.getName(), loi.getPageName());
        link = "https://pathfinderwiki.com/wiki/" + loi.getPageName().replace(' ', '_');
        type = loi.getType();
        text = loi.getText();
        articleLength = loi.getArticleLength()/100*100;
	}
}
