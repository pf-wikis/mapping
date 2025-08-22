package io.github.pfwikis.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.github.pfwikis.Helper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({"labels", "link", "modificationDate", "type", "capital", "size", "filterMinzoom", "articleLength", "text"})
public class Properties {

    private String labels;
    private String link;
    private Boolean capital;
    private Integer size;
    private String type;
    private String text;
    private int articleLength;
    private Instant modificationDate;

    public Properties(Location loc) {
        labels = Helper.handleName(loc.getName(), loc.getPageName());
        link = "https://pathfinderwiki.com/wiki/" + loc.getPageName().replace(' ', '_');
        capital = loc.getCapital();
        type = loc.getLoiType();
        modificationDate = loc.getModificationDate();
    }
}
