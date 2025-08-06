package io.github.pfwikis.model;

import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
  @JsonSubTypes({
    @Type(value = Geometry.Polygon.class, name = "Polygon"),
    @Type(value = Geometry.MultiPolygon.class, name = "MultiPolygon"),
    @Type(value = Geometry.Point.class, name = "Point"),
    @Type(value = Geometry.MultiPoint.class, name = "MultiPoint"),
    @Type(value = Geometry.LineString.class, name = "LineString"),
    @Type(value = Geometry.MultiLineString.class, name = "MultiLineString")
  })
public interface Geometry {
	
	public Stream<LngLat> streamPoints();

    @Data
    public static class Polygon implements Geometry {
        private List<List<LngLat>> coordinates;
        
        @Override
        public Stream<LngLat> streamPoints() {
        	return coordinates.stream().flatMap(List::stream);
        }
    }

    @Data
    public static class MultiPolygon implements Geometry {
        private List<List<List<LngLat>>> coordinates;
        
        @Override
        public Stream<LngLat> streamPoints() {
        	return coordinates.stream().flatMap(List::stream).flatMap(List::stream);
        }
    }
    
    @Data
    public static class Point implements Geometry {
        private LngLat coordinates;
        
        @Override
        public Stream<LngLat> streamPoints() {
        	return Stream.of(coordinates);
        }
    }
    
    @Data
    public static class MultiPoint implements Geometry {
        private List<LngLat> coordinates;
        
        @Override
        public Stream<LngLat> streamPoints() {
        	return coordinates.stream();
        }
    }
    
    public static interface ILineString {
    	List<List<LngLat>> toLines();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineString implements Geometry, ILineString {
        private List<LngLat> coordinates;
        
        @Override
        public Stream<LngLat> streamPoints() {
        	return coordinates.stream();
        }

		@Override
		public List<List<LngLat>> toLines() {
			return List.of(coordinates);
		}
    }
    
    @Data
    public static class MultiLineString implements Geometry, ILineString {
        private List<List<LngLat>> coordinates;
        
        @Override
        public Stream<LngLat> streamPoints() {
        	return coordinates.stream().flatMap(List::stream);
        }

		@Override
		public List<List<LngLat>> toLines() {
			return coordinates;
		}
    }
}
