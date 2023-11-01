package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import mil.nga.geopackage.GeoPackageManager;

@Slf4j
public class Cleaner {

    public static void main(String[] args) throws IOException {
        new Cleaner().run();
    }

    public void run() throws IOException {
    	var root = new File("../sources");
    	log.info("Starting cleaning in {}", root.getAbsolutePath());
        Files.walk(root.toPath())
            .map(Path::toFile)
            .filter(f->f.getName().endsWith(".gpkg"))
            .forEach(this::clean);
    }

    private void clean(File f) {
    	log.info("Cleaning {}", f);
        try(var geoPackage = GeoPackageManager.open(f)) {
            for(var featureTable:geoPackage.getFeatureTables()) {
            	log.info("Cleaning {}: {}", f, featureTable);
                var featureDao = geoPackage.getFeatureDao(featureTable);
                var result=featureDao.queryForAll();
                while(result.moveToNext()) {
                    if(result.getGeometry() == null || result.getGeometry().isEmpty()) {
                        geoPackage.beginTransaction();
                        log.info("Removing "+result.getId()+" from "+f);
                        featureDao.deleteById(result.getId());
                        geoPackage.endTransaction(true);
                    }
                }
            }
        }
    }

}
