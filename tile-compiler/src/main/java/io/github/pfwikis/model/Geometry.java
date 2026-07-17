package io.github.pfwikis.model;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

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
	
	Stream<LngLat> streamPoints();
	long size();
	void transformPoints(UnaryOperator<LngLat> transformer);
	
	@Data
	static abstract class AbstractGeometry<T> implements Geometry {
		protected T coordinates;
	}
	
	@EqualsAndHashCode(callSuper = true)
	static abstract class AbstractGeometryLP extends AbstractGeometry<List<LngLat>> {
		@Override
        public Stream<LngLat> streamPoints() {
        	return coordinates.stream();
        }
		
		@Override
		public long size() {
			return coordinates.size();
		}
		
		@Override
		public void transformPoints(UnaryOperator<LngLat> transformer) {
			var nullsafe = transformer.andThen(Objects::requireNonNull);
			coordinates = coordinates.stream().map(nullsafe).toList();
		}
	}
	
	@EqualsAndHashCode(callSuper = true)
	static abstract class AbstractGeometryLLP extends AbstractGeometry<List<List<LngLat>>> {
		@Override
        public Stream<LngLat> streamPoints() {
        	return coordinates.stream().flatMap(List::stream);
        }
		
		@Override
		public long size() {
			return coordinates.stream().mapToLong(List::size).sum();
		}
		
		@Override
		public void transformPoints(UnaryOperator<LngLat> transformer) {
			var nullsafe = transformer.andThen(Objects::requireNonNull);
			coordinates = coordinates.stream()
					.map(r->r.stream().map(nullsafe).toList())
					.toList();
		}
	}
	
	@EqualsAndHashCode(callSuper = true)
	static abstract class AbstractGeometryLLLP extends AbstractGeometry<List<List<List<LngLat>>>> {
		@Override
        public Stream<LngLat> streamPoints() {
			return coordinates.stream().flatMap(List::stream).flatMap(List::stream);
        }
		
		@Override
		public long size() {
			return coordinates.stream().flatMap(List::stream).mapToLong(List::size).sum();
		}
		
		@Override
		public void transformPoints(UnaryOperator<LngLat> transformer) {
			var nullsafe = transformer.andThen(Objects::requireNonNull);
			coordinates = coordinates.stream()
				.map(r->r.stream()
					.map(rr->rr.stream()
						.map(nullsafe)
						.toList())
					.toList())
				.toList();
		}
	}

    @EqualsAndHashCode(callSuper = true)
    static class Polygon extends AbstractGeometryLLP {}

    @EqualsAndHashCode(callSuper = true)
    static class MultiPolygon extends AbstractGeometryLLLP {}
    
    @EqualsAndHashCode(callSuper = true)
    static class Point extends AbstractGeometry<LngLat> {
        
        @Override
        public Stream<LngLat> streamPoints() {
        	return Stream.of(coordinates);
        }

		@Override
		public long size() {
			return 1;
		}

		@Override
		public void transformPoints(UnaryOperator<LngLat> transformer) {
			coordinates = transformer.andThen(Objects::requireNonNull).apply(coordinates);
		}
    }
    
    @EqualsAndHashCode(callSuper = true)
    static class MultiPoint extends AbstractGeometryLP {}
    
    static interface ILineString {
    	List<List<LngLat>> toLines();
    }
    
    @EqualsAndHashCode(callSuper = true)
    static class LineString extends AbstractGeometryLP implements ILineString {
		@Override
		public List<List<LngLat>> toLines() {
			return List.of(coordinates);
		}

		public static LineString from(List<LngLat> of) {
			var r = new LineString();
			r.coordinates = of;
			return r;
		}
    }
    
    @EqualsAndHashCode(callSuper = true)
    static class MultiLineString extends AbstractGeometryLLP implements ILineString {
		@Override
		public List<List<LngLat>> toLines() {
			return coordinates;
		}
    }
}
