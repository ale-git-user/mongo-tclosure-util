package com.termmed.importer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.termmed.util.DefinitionLoader;
import com.termmed.util.TClosure;
import org.bson.Document;

import java.io.IOException;

public class DefinitionImporter {

    public void importToMongo(String file, String concreteRelsFile, String server,String db, String collectionPrefix, String port,String pathId, String descFile,String concFile, String langCode) throws IOException {

        System.out.println("connecting to mongo");
        MongoClient mongoClient = MongoClients.create("mongodb://" + server + ":" + port);

        System.out.println("getting db");
        MongoDatabase database = mongoClient.getDatabase(db);


        System.out.println("getting previous collections");
        MongoCollection<Document> definitionCollection=database.getCollection(collectionPrefix + "definition" + pathId );

        System.out.println("dropping previous collections");
        try {
            definitionCollection.drop();
        }catch (Exception e){
            System.out.println("Error dropping previous collections:" + e.getMessage());
        }
        DefinitionLoader dLoader;
        dLoader = new DefinitionLoader(file, concreteRelsFile, descFile, concFile,langCode, null);
        System.out.println("sending data to collections");
        dLoader.toMongo(definitionCollection);

        System.out.println("creating indexes in collections");
        definitionCollection.createIndex(Indexes.ascending("c"));
        definitionCollection.createIndex(Indexes.ascending("g.rg.t","g.rg.d"));
        definitionCollection.createIndex(Indexes.ascending("m"));
        definitionCollection.createIndex(Indexes.ascending("st"));
        definitionCollection.createIndex(Indexes.ascending("p"));

        System.out.println("closing mongo connection");
        mongoClient.close();
        dLoader=null;
    }


}
