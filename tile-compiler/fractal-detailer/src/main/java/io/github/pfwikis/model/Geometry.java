package io.github.pfwikis.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

@Data
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
  @JsonSubTypes({
    @Type(value = Geometry.Polygon.class, name = "Polygon"),
    @Type(value = Geometry.MultiPolygon.class, name = "MultiPolygon")
  })
public abstract class Geometry {

    @Data
    public static class Polygon extends Geometry {
        private List<List<LngLat>> coordinates;
    }

    @Data
    public static class MultiPolygon extends Geometry {
        private List<List<List<LngLat>>> coordinates;
    }
}
