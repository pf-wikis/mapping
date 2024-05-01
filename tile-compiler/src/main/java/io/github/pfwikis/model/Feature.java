package io.github.pfwikis.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Feature extends AnyJson {

	private Geometry geometry;
    private Properties properties = new Properties();
    private Tippecanoe tippecanoe = new Tippecanoe();
    
    public void setProperties(Properties properties) {
    	//ensures this is never null
    	if(properties != null)
    		this.properties = properties;
	}
    
    public void setTippecanoe(Tippecanoe tippecanoe) {
    	//ensures this is never null
    	if(tippecanoe != null)
    		this.tippecanoe = tippecanoe;
	}
    
    @Data
    public static class Tippecanoe {
    	private Integer minzoom;
    	private Integer maxzoom;
    }
}
