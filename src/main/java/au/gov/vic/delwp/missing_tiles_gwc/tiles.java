/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package au.gov.vic.delwp.missing_tiles_gwc;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author th61
 */
public class tiles {

    public static String output;
    public static File shapefile;
    public static ShapefileDataStore store;
    public static SimpleFeatureType TYPE;
    public static SimpleFeatureBuilder builder;
    public static SimpleFeatureStore fs;
    public static List<SimpleFeature> features = new ArrayList<>();
    public static Transaction trans = new DefaultTransaction("create");

    public static void main(String[] args) {
        Options ops = new Options();
        Option projection = new Option("p", "proj", true, "Projectiom number 3111 or 3857");
        Option cachedir = new Option("c", "cache-dir", true, "Directory of cache");
        Option resolution = new Option("r", "resolution", true, "Tile level resolution");
        Option tilepixels = new Option("px", "tile-width", true, "Tile pixel width/height");
        Option outputdir = new Option("o", "output-dir", true, "Output directory");
        Option format = new Option("f", "format", true, "File format the program is looking for jpg,png,png8");
        projection.setRequired(true);
        cachedir.setRequired(true);
        resolution.setRequired(true);
        tilepixels.setRequired(true);
        outputdir.setRequired(true);
        format.setRequired(true);
        ops.addOption(outputdir)
                .addOption(tilepixels)
                .addOption(resolution)
                .addOption(cachedir)
                .addOption(projection)
                .addOption(format);
        CommandLineParser parser = new GnuParser(); // <-- Deprecated in commons-cli 1.3 !!!
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        try {
            cmd = parser.parse(ops, (args.length == 0 ? new String[0] : args));
        } catch (ParseException e) {
            System.out.println("missing cmd line arguments");
            System.out.println(e);
            formatter.printHelp("utility-name", ops);
            System.exit(1);
            return;
        }
        
        
        //int proj = 3857;
        int proj = Integer.parseInt(cmd.getOptionValue("proj"));
        double[] vgOriginTL = {1786000, 3081000};
        double[] vgOrigin = {1786000, (vgOriginTL[1]-(512*2116.670900008467))};
        double[] wmOrigin = {-20037508.34, -20037508.34};
        //String cacheLoc = "\\\\NASA02\\public\\Data\\VicmapAPI_2015Metro_wm_imagry\\vmapi_metro2015_vg\\EPSG_3857_512_14";
        String cacheLoc = cmd.getOptionValue("cache-dir");
        String fileformat = cmd.getOptionValue("format");
        //double res = 4.777314267158508*2;
        double res = Double.parseDouble(cmd.getOptionValue("resolution"));
        //int tilepx = 512;
        int tilepx = Integer.parseInt(cmd.getOptionValue("tile-width"));
        //output = "C:\\Data\\missing_tiles_gwc\\";
        output = cmd.getOptionValue("output-dir");
        PrecisionModel pm = new PrecisionModel();
        GeometryFactory gf = new GeometryFactory(pm, proj);

        //create file list array
        ArrayList<String> files = getFiles(cacheLoc,fileformat);
        System.out.println(files.size());
        long start = System.currentTimeMillis();
        Collections.sort(files);
        System.out.println("Time for file array sort: " + (System.currentTimeMillis() - start) + " ms");
        int lastCol = 0;
        int lastRow = 0;
        createShapefile(cacheLoc, output, proj);
        //double[] lastBounds = null;
        double[] largebounds = null;
        for (String file : files) {
            //System.out.println(file); // they are in order of columns
            double[] coords = null;
            if (proj == 3111) {
                coords = vgOrigin;
            } else {
                coords = wmOrigin;
            }
            String folder = file.split("\\|")[1];
            //String f = file.split("\\.")[1].split("\\|")[0];
            int col = Integer.parseInt(file.split("\\.")[0].split("_")[0]);
            int row = Integer.parseInt(file.split("\\.")[0].split("_")[1]);

            double[] bounds = getTilebounds(coords, row, col, tilepx, res);
            if (lastCol == col && lastRow == (row - 1)) {
                //System.out.println("extending bounds");
                largebounds[3] = bounds[3];
//                holebounds[0] = largebounds[0];
//                holebounds[1] = largebounds[3];
//                holebounds[2] = largebounds[2];
//                holebounds[3] = largebounds[3];
            } else {
                if (largebounds != null) {
                    addFeatureToStore(largebounds, pm, gf);
                }
//                if (holebounds[0])
//                {   
//                    holebounds[3] = bounds[1];
//                    addFeatureToStore(holebounds, pm, gf);
//                }
//System.out.println("!!new bounds!!");
                largebounds = bounds;
                
            }
            //System.out.println(file.split("\\.")[0]);
            //System.out.println(lastCol + "_" + lastRow);
            //System.out.println(String.format("%.2f", largebounds[0]) + "," + String.format("%.2f", largebounds[1]) + "," + String.format("%.2f", largebounds[2]) + "," + String.format("%.2f", largebounds[3]));

            lastCol = col;
            lastRow = row;
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
        try {
            try {
                System.out.println("Adding "+collection.size()+" to shapefile.");
                fs.addFeatures(collection);
                trans.commit();
            } catch (Exception e) {
                e.printStackTrace();
                trans.rollback();
            } finally {
                trans.close();
            }
        } catch (IOException e) {
            System.out.println("Read write faled on shapefile.");
        }
        //shapefile.
        //System.exit(0);
    }

    public static void createShapefile(String cacheLoc, String output, int proj) {
        try {
            TYPE = DataUtilities.createType("TileArea",
                    "the_geom:MultiPolygon:srid=" + proj
            //"name:String," +   // <- a String attribute
            //"number:Integer"   // a number attribute
            );
        } catch (SchemaException e) {
            e.printStackTrace();
        }
        builder = new SimpleFeatureBuilder(TYPE);
        String[] a = cacheLoc.split("\\\\");
        String name = a[a.length - 1];
        shapefile = new File(output + "\\" + name + "_coverage.shp");
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        try {
            params.put("url", shapefile.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);
            store = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
            store.createSchema(TYPE);
            SimpleFeatureSource fsource = store.getFeatureSource(store.getTypeNames()[0]);
            SimpleFeatureType ft = store.getSchema();
            fs = (SimpleFeatureStore) fsource;
            fs.setTransaction(trans);
        } catch (MalformedURLException e) {
            System.out.println("Shapefile URL failed");
        } catch (IOException e) {
            System.out.println("IO Exception");
            e.printStackTrace();
        }

    }

    public static void addFeatureToStore(double[] largebounds, PrecisionModel pm, GeometryFactory gf) {
        // LinearRing ring = new LinearRing(
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(largebounds[0], largebounds[1]),
            new Coordinate(largebounds[2], largebounds[1]),
            new Coordinate(largebounds[2], largebounds[3]),
            new Coordinate(largebounds[0], largebounds[3]),
            new Coordinate(largebounds[0], largebounds[1])
        };
        Polygon poly = gf.createPolygon(gf.createLinearRing(coords));
        //System.out.println(poly.toString());
        builder.add(poly);
        SimpleFeature feature = builder.buildFeature(null);
        features.add(feature);

    }
    public static double[] getTilebounds(double[] coords, int row, int col, int tilepx, double res) {
        double tiledist = res * tilepx;
        double minX = coords[0] + (tiledist * col);
        double minY = coords[1] + (tiledist * row);
        double maxX = minX + tiledist;
        double maxY = minY + tiledist;

        return new double[]{minX, minY, maxX, maxY};
    }

    public static ArrayList<String> getFiles(String cacheLoc, String fileformat) {
        File f = new File(cacheLoc);
        long start = System.currentTimeMillis();
        String[] folders = null;
        ArrayList<String> files = new ArrayList<String>();
        try {
            folders = f.list();
            for (String folder : folders) {
                File n = new File(cacheLoc + "\\" + folder);
                //files.addAll(n.list());
                for (String file : n.list()) {
                    if (fileformat.equals(file.split("\\.")[1])) files.add(file + "|" + folder);
                }
            }
        } catch (Exception e) {
            System.out.println("File List failed");
        }
        System.out.println("Time for file list creation: " + (System.currentTimeMillis() - start) + " msecs");
        return files;
    }
}
