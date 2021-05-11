package com.termmed.importer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.termmed.util.TClosure;
import org.bson.Document;

import java.io.IOException;

public class TClosureImporter {

    public void importToMongo(String file, String server,String db, String collectionPrefix, String port,String pathId) throws IOException {

        System.out.println("connecting to mongo");
        MongoClient mongoClient = MongoClients.create("mongodb://" + server + ":" + port);

        System.out.println("getting db");
        MongoDatabase database = mongoClient.getDatabase(db);


        System.out.println("getting previous collections");
        MongoCollection<Document> descCollection=database.getCollection(collectionPrefix + "descendant" + pathId );
        MongoCollection<Document> ancesCollection=database.getCollection(collectionPrefix + "ancestor" + pathId );

        System.out.println("dropping previous collections");
        try {
            descCollection.drop();
            ancesCollection.drop();
        }catch (Exception e){
            System.out.println("Error dropping previous collections:" + e.getMessage());
        }
        TClosure tClos;
        tClos = new TClosure(file);
        System.out.println("sending data to collections");
        tClos.toMongo(descCollection, ancesCollection);

        System.out.println("creating indexes in collections");
        descCollection.createIndex(Indexes.ascending("c"));
        ancesCollection.createIndex(Indexes.ascending("c"));

        System.out.println("closing mongo connection");
        mongoClient.close();
        tClos=null;
    }


}
