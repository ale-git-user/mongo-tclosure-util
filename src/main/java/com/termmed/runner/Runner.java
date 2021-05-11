package com.termmed.runner;

import com.termmed.importer.DefinitionImporter;
import com.termmed.importer.RefsetsImporter;
import com.termmed.importer.TClosureImporter;
import com.termmed.utilities.FileHelper;

import java.io.File;

public class Runner {

    public static void main(String[] args) throws Exception {


        if (args[0].contains("-ALL2MONGO")) {

            if (args.length != 8) {
                throw new Exception("Wrong params number");
            }
            File folder=new File (args[1]);
            if (!folder.exists() || !folder.isDirectory()){
                throw new Exception("Wrong folder param");
            }
            // Creating transitive closure index
            String relsFile=FileHelper.getFile(folder,"rf2-relationships",null,null,"stated");

            TClosureImporter tClosureImporter = new TClosureImporter();
            tClosureImporter.importToMongo(relsFile, args[2], args[3], args[4], args[5], args[6]);
            tClosureImporter = null;


            // Creating concept definition index
            String conceptFile=FileHelper.getFile(folder,"rf2-concepts",null,null,"stated");
            String descFile=FileHelper.getFile(folder,"rf2-descriptions",null,null,"stated");

            DefinitionImporter definitionImporter = new DefinitionImporter();
            definitionImporter.importToMongo(relsFile, args[2], args[3], args[4], args[5], args[6], descFile, conceptFile, args[7]);
            definitionImporter = null;

            // Creating concept refsets index
            RefsetsImporter refsetsImporter = new RefsetsImporter();
            refsetsImporter.importToMongo(args[1],args[2],args[3],args[4],args[5],args[6]);
            refsetsImporter=null;

        }else if (args[0].contains("-DEF2MONGO")) {

            if (args.length<10){
                throw new Exception("Wrong params number");
            }
            TClosureImporter tClosureImporter = new TClosureImporter();
            tClosureImporter.importToMongo(args[1],args[2],args[3],args[4],args[5],args[6]);
            tClosureImporter=null;


            DefinitionImporter definitionImporter = new DefinitionImporter();
            definitionImporter.importToMongo(args[1],args[2],args[3],args[4],args[5],args[6], args[7],args[8],args[9]);
            definitionImporter=null;

        }else if (args[0].contains("-REF2MONGO")){

            RefsetsImporter refsetsImporter = new RefsetsImporter();
            if (args.length>10){
                refsetsImporter.importToMongo(args[10],args[2],args[3],args[4],args[5],args[6]);
            }else if (args.length==7){
                refsetsImporter.importToMongo(args[1],args[2],args[3],args[4],args[5],args[6]);
            }else{
                throw new Exception("Wrong params number");
            }

            refsetsImporter=null;
        }
    }
}
