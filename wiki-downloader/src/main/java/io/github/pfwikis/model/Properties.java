package io.github.pfwikis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"Name", "link", "type", "capital", "size"})
public class Properties {

    @JsonProperty("Name")
    private String Name;
    private String link;
    private Boolean capital;
    private Integer size;
    private String type;
}
