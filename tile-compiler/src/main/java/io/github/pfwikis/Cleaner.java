package io.github.pfwikis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import mil.nga.geopackage.GeoPackageManager;

public class Cleaner {

    public static void main(String[] args) throws IOException {
        new Cleaner().run();
    }

    public void run() throws IOException {
        Files.walk(new File("../sources").toPath())
            .map(Path::toFile)
            .filter(f->f.getName().endsWith(".gpkg"))
            .forEach(this::clean);
    }

    private void clean(File f) {
        try(var geoPackage = GeoPackageManager.open(f)) {
            for(var featureTable:geoPackage.getFeatureTables()) {
                var featureDao = geoPackage.getFeatureDao(featureTable);
                var result=featureDao.queryForAll();
                while(result.moveToNext()) {
                    if(result.getGeometry() == null || result.getGeometry().isEmpty()) {
                        geoPackage.beginTransaction();
                        System.out.println("Removing "+result.getId()+" from "+f);
                        featureDao.deleteById(result.getId());
                        geoPackage.endTransaction(true);
                    }
                }
            }
        }
    }

}
