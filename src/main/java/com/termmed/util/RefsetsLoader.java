package com.termmed.util;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieStore;
import java.util.*;

public class RefsetsLoader {
    private final HashSet<String> refsetFiles;
    private final ArrayList<String> notEnabledToIndex;
    private HashMap<String, TreeSet<String>> refsets;

    public RefsetsLoader(HashSet<String> refsetFiles, ArrayList<String> notEnabledToIndex) throws IOException {
        this.refsetFiles=refsetFiles;
        this.notEnabledToIndex=notEnabledToIndex;
        refsets=new HashMap<String, TreeSet<String>>();
        loadRefsets();
    }

    private void loadRefsets( ) throws IOException {
        String module;
        for (String fileString : refsetFiles) {
            System.out.println("Starting Refset rows from: " + fileString);
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileString), "UTF8"));
            try {
                String line = br.readLine();
                line = br.readLine(); // Skip header
                int count = 0;
                while (line != null) {
                    if (line.trim().equals("")) {
                        continue;
                    }
                    String[] columns = line.split("\t",-1);
                    if (columns[2].equals("1") && !notEnabledToIndex.contains(columns[4])){
                        TreeSet<String>tmap=refsets.get(columns[4]);
                        if (tmap==null){
                            tmap=new TreeSet<String>();
                        }
                        tmap.add(columns[5]);
                        refsets.put(columns[4],tmap);
                        count++;
                        if (count % 100000 == 0) {
                            System.out.print(".");
                        }
                    }
                    line = br.readLine();
                }
                System.out.println(".");
                System.out.println("total Refets loaded = " + refsets.size());
            } finally {
                br.close();
            }

        }
    }

    public void createDumpCollections(String outputFolder, String db, String collectionPrefix, String pathId) throws IOException {

        MetadataGenerator metadataGenerator = new MetadataGenerator(outputFolder + "/" + collectionPrefix + "refsets" + pathId + ".metadata.json");
        String metadata = Constants.REFSET_METADATA_JSON.replaceAll("===DB===", db);
        metadata = metadata.replaceAll("===COLL===", collectionPrefix);
        metadata = metadata.replaceAll("===PATH===", pathId);
        metadataGenerator.generate(metadata);
        metadataGenerator.close();
        metadataGenerator = null;

        BsonGenerator bsonGenerator = new BsonGenerator(outputFolder + "/" + collectionPrefix + "refsets" + pathId + ".bson");

        for (String refsetId : refsets.keySet()) {
            TreeSet<String> refsetList= refsets.get(refsetId);
            Document doc= new Document("r", refsetId).append("s", refsetList);
            bsonGenerator.generate(doc);
        }
        bsonGenerator.close();
    }
    public void toMongo( MongoCollection<Document> collection) throws IOException {
        for (String refsetId : refsets.keySet()) {
            TreeSet<String> refsetList= refsets.get(refsetId);
            Document doc= new Document("r", refsetId).append("s", refsetList);
            collection.insertOne(doc);
        }
    }
}
