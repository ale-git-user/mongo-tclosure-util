package com.termmed.importer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.termmed.util.RefsetsLoader;
import org.bson.Document;

import javax.naming.directory.BasicAttribute;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class RefsetsImporter {
    private ArrayList<String> notEnabledToIndex;
    private HashSet<String> refsetFiles;

    public void importToMongo(String snapShotReleaseFolder, String server, String db, String collectionPrefix, String port, String pathId) throws IOException {

        System.out.println("connecting to mongo");
        MongoClient mongoClient = MongoClients.create("mongodb://" + server + ":" + port);

        System.out.println("getting db");
        MongoDatabase database = mongoClient.getDatabase(db);


        System.out.println("getting previous collections");
        MongoCollection<Document> refCollection=database.getCollection(collectionPrefix + "refsets" + pathId );

        System.out.println("dropping previous collections");
        try {
            refCollection.drop();
        }catch (Exception e){
            System.out.println("Error dropping previous collections:" + e.getMessage());
        }
        refsetFiles=new HashSet<String>();
        notEnabledToIndex=new ArrayList<String>(Arrays.asList(
                "900000000000497000",
                "900000000000490003",
                "900000000000498005",
                "900000000000489007",
                "900000000000527005",
                "900000000000523009",
                "900000000000524003",
                "900000000000528000",
                "900000000000526001",
                "723561005",
                "723562003",
                "723560006",
                "900000000000530003",
                "723563008",
                "723589008",
                "723592007",
                "900000000000525002",
                "900000000000513000",
                "900000000000456007",
                "900000000000534007",
                "900000000000512005",
                "900000000000516008",
                "900000000000530003",
                "900000000000538005",
                "447570008",
                "705109006",
                "733619002",
                "733073007",
                "762103008",
                "762676003"));

        processFolderRec(new File(snapShotReleaseFolder));

        RefsetsLoader rLoader;
        rLoader=new RefsetsLoader(refsetFiles, notEnabledToIndex);

        System.out.println("sending data to collections");
        rLoader.toMongo(refCollection);

        System.out.println("creating indexes in collections");
        refCollection.createIndex(Indexes.ascending("r"));


        System.out.println("closing mongo connection");
        mongoClient.close();
        rLoader=null;


    }

    private void processFolderRec(File coreFolder) {
        if (coreFolder.isDirectory()) {
            File[] list = coreFolder.listFiles();
            for (File file : list) {
                processFolderRec(file);
            }
        } else {
            if (isRefsetFile(coreFolder)){
                refsetFiles.add(coreFolder.getAbsolutePath());
            }
        }
    }

    private boolean isRefsetFile(File coreFolder) {
        boolean ret = false;
        try {
            if (nameIsEnable(coreFolder.getName())) {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(coreFolder), "UTF-8"));
                String line = br.readLine();
                if (line.startsWith("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId")) {
                    ret = true;
                }
                br.close();
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        return ret;
    }

    private boolean nameIsEnable(String name) {
        String normName=name.toLowerCase();
        if (normName.contains("_language")
        || normName.contains("refsetdescriptor")
                || normName.contains("moduledependency")
                || normName.contains("_descriptiontype")
                || normName.contains("mrcmattribute")
                || normName.contains("mrcmdomain")
                || normName.contains("mrcmmodule")
                || normName.contains("owlexpression")){
            return false;
        }
        return true;
    }

}
