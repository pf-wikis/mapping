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
@JsonPropertyOrder({"Name", "link", "type", "capital", "size", "filterMinzoom"})
public class Properties {

    @JsonProperty("Name")
    private String Name;
    private String link;
    private Boolean capital;
    private Integer size;
    private String type;
    private String text;
    private int articleLength;

    public Properties(City city) {
        Name = Helper.handleName(city.getName(), city.getPageName());
        link = "https://pathfinderwiki.com/wiki/" + city.getPageName().replace(' ', '_');
        capital = city.isCapital();
        text = city.getText();
        articleLength = city.getArticleLength();
    }

	public Properties(LoI loi) {
		Name = Helper.handleName(loi.getName(), loi.getPageName());
        link = "https://pathfinderwiki.com/wiki/" + loi.getPageName().replace(' ', '_');
        type = loi.getType();
        text = loi.getText();
        articleLength = loi.getArticleLength();
	}
}
