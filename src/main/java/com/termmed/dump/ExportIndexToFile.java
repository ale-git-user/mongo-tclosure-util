package com.termmed.dump;

import com.termmed.util.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class ExportIndexToFile {
    private final String outputFolder;
    HashSet<String> refsetFiles;

    public ExportIndexToFile(String[] args) {
        outputFolder = args[8] + "/indexes/" + args[2] + "/" + args[3] + "/" + args[6] + args[0] + "/" + args[3] ;
        File outputFolderFile=new File(outputFolder);
        outputFolderFile.mkdirs();
    }

    public void exportTClosure(String file, String db, String collectionPrefix,String pathId) throws IOException {

        TClosure tClos;
        tClos = new TClosure(file);
        tClos.createDumpCollections(outputFolder, db, collectionPrefix, pathId);
        tClos=null;
    }
    public void exportStatedTClosureAndDefinition(String owlFile, String db, String collectionPrefix,String pathId, String descFile, String concFile, String langCode, LanguageFallbackProcessor languageFallbackProcessor) throws Exception {
        System.out.println("Stated data files index process");
        System.out.println("loading definition metadata data to files");

        DefinitionLoader definitionLoader=new DefinitionLoader(descFile, concFile, langCode, languageFallbackProcessor);
        TClosure tClos;
        tClos = new TClosure();
        System.out.println("Instancing definition and trans.closure loader object");
        TClosureAndDefinitionOwlLoader tClosureAndDefinitionOwlLoader=new TClosureAndDefinitionOwlLoader(tClos, definitionLoader, owlFile);
        tClosureAndDefinitionOwlLoader.load();

        System.out.println("sending transitive closure data to files");
        tClos.createDumpCollections(outputFolder, db, collectionPrefix + "st", pathId);
        tClos=null;

        System.out.println("sending definition data to files");
        definitionLoader.createDumpCollections(outputFolder, db, collectionPrefix + "st", pathId);
        System.out.println("end sending data to files");
        definitionLoader=null;
        System.out.println("End stated data files index process");
    }

    public void exportStatedTClosureAndDefinition(String owlFile, String db, String collectionPrefix,String pathId, HashMap<String, DescriptionData> descriptions, HashMap<String, ConceptData> concepts,  String langCode, LanguageFallbackProcessor languageFallbackProcessor) throws Exception {
        System.out.println("Stated data files index process");
        System.out.println("loading definition metadata data to files");

        DefinitionLoader definitionLoader=new DefinitionLoader(descriptions, concepts, langCode, languageFallbackProcessor);
        TClosure tClos;
        tClos = new TClosure();
        System.out.println("Instancing definition and trans.closure loader object");
        TClosureAndDefinitionOwlLoader tClosureAndDefinitionOwlLoader=new TClosureAndDefinitionOwlLoader(tClos, definitionLoader, owlFile);
        tClosureAndDefinitionOwlLoader.load();

        System.out.println("sending transitive closure data to files");
        tClos.createDumpCollections(outputFolder, db, collectionPrefix + "st", pathId);
        tClos=null;

        System.out.println("sending definition data to files");
        definitionLoader.createDumpCollections(outputFolder, db, collectionPrefix + "st", pathId);
        System.out.println("end sending data to files");
        definitionLoader=null;
        System.out.println("End stated data files index process");
    }
    public void exportRefset(String snapShotReleaseFolder, String db, String collectionPrefix, String pathId) throws IOException {

        refsetFiles = new HashSet<String>();
        ArrayList<String> notEnabledToIndex = new ArrayList<String>(Arrays.asList(
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
        rLoader.createDumpCollections(outputFolder, db, collectionPrefix, pathId);
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
    public void exportDefinition(String relsFile, String concreteRelsFile, String db, String collectionPrefix, String pathId, String descFile, String concFile, String langCode, LanguageFallbackProcessor languageFallbackProcessor) throws IOException {

        DefinitionLoader dLoader;
        dLoader = new DefinitionLoader(relsFile, concreteRelsFile, descFile, concFile,langCode, languageFallbackProcessor);
        System.out.println("sending data to files");
        dLoader.createDumpCollections(outputFolder, db, collectionPrefix, pathId);
        System.out.println("end sending data to files");
        dLoader=null;

    }
    public void exportDefinition(String relsFile, String concreteRelsFile, String db, String collectionPrefix, String pathId, HashMap<String, DescriptionData> descriptions, HashMap<String, ConceptData> concepts, String langCode, LanguageFallbackProcessor languageFallbackProcessor) throws IOException {

        DefinitionLoader dLoader;
        dLoader = new DefinitionLoader(relsFile, concreteRelsFile, descriptions, concepts,langCode, languageFallbackProcessor);
        System.out.println("sending data to files");
        dLoader.createDumpCollections(outputFolder, db, collectionPrefix, pathId);
        System.out.println("end sending data to files");
        dLoader=null;

    }

}
