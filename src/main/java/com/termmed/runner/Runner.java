package com.termmed.runner;

import com.termmed.dump.ExportIndexToFile;
import com.termmed.importer.DefinitionImporter;
import com.termmed.importer.RefsetsImporter;
import com.termmed.importer.TClosureImporter;
import com.termmed.util.*;
import com.termmed.utilities.FileHelper;

import java.io.*;
import java.util.*;

public class Runner {

    static HashMap<String, ConceptData> concepts=null;
    static HashMap<String, DescriptionData> descriptions=null;
    public static void main(String[] args) throws Exception {
        long start=new Date().getTime();
        if (args[0].contains("-ALL_INDEX")) {

            if (args.length != 10) {
                throw new Exception("Wrong params number");
            }
            File folder = new File(args[1]);
            if (!folder.exists() || !folder.isDirectory()) {
                throw new Exception("Wrong folder param");
            }
            // Creating transitive closure index
            String relsFile = FileHelper.getFile(folder, "rf2-relationships", null, null, "stated");

            ExportIndexToFile exportIndexToFile = new ExportIndexToFile(args);
            exportIndexToFile.exportTClosure(relsFile, args[3], args[4], args[6]);

            // Creating concept definition index
            String concreteRelsFile = FileHelper.getFile(folder, "rf2-inferred-concrete-domains", null, null, "stated");
            String conceptFile = FileHelper.getFile(folder, "rf2-concepts", null, null, "stated");
            String descFile = FileHelper.getFile(folder, "rf2-descriptions", null, null, "stated");

            concepts=null;
            descriptions=null;
            if (conceptFile!=null && descFile!=null) {
                loadConceptsAndDescriptions(conceptFile, descFile, args[7]);
                LanguageFallbackProcessor languageFallbackProcessor = null;
                if (args[0].contains("WITH_PREF")) {
                    languageFallbackProcessor = getLanguageFallbackProcessor(args[9], folder, descFile);
                    if (languageFallbackProcessor != null && descriptions != null) {
                        getPreferreds(languageFallbackProcessor);
                    }
                }
                exportIndexToFile.exportDefinition(relsFile, concreteRelsFile, args[3], args[4], args[6], descriptions, concepts, args[7], languageFallbackProcessor);

                // Creating concept refsets index
                exportIndexToFile.exportRefset(args[1], args[3], args[4], args[6]);

                // Creating stated trans.closure and definition index
                String owlFile = FileHelper.getFile(folder, "rf2-owl-expression", null, "expression", null);
                exportIndexToFile.exportStatedTClosureAndDefinition(owlFile, args[3], args[4], args[6], descriptions, concepts, args[7], languageFallbackProcessor);
            }

        }else if (args[0].contains("-STATED_INFERRED_INDEX")){
            System.out.println("*********************** step 1 ********************");
            if (args.length != 10) {
                throw new Exception("Wrong params number");
            }
            File folder=new File (args[1]);
            if (!folder.exists() || !folder.isDirectory()){
                throw new Exception("Wrong classify output folder param.");
            }
            // Creating transitive closure index
            String relsFile=FileHelper.getFile(folder,"rf2-relationships",null,"snapshot","stated");

            ExportIndexToFile exportIndexToFile=new ExportIndexToFile(args);
            exportIndexToFile.exportTClosure(relsFile,args[3],args[4],args[6]);

            // Creating concept definition index
            String concreteRelsFile=FileHelper.getFile(folder,"rf2-inferred-concrete-domains",null,"snapshot",null);
            String conceptFile=FileHelper.getFile(folder,"rf2-concepts",null,null,"stated");
            String descFile=FileHelper.getFile(folder,"rf2-descriptions",null,null,"stated");

            concepts=null;
            descriptions=null;
            if (conceptFile!=null && descFile!=null) {
                loadConceptsAndDescriptions(conceptFile, descFile, args[7]);

                System.out.println("**** Total Concepts :" + concepts.size() + " **** Total descriptions:" +  descriptions.size());
                LanguageFallbackProcessor languageFallbackProcessor = null;
                if (args[0].contains("WITH_PREF")) {
                    languageFallbackProcessor = getLanguageFallbackProcessor(args[9], folder, descFile);

                    if (languageFallbackProcessor != null && descriptions != null) {
                        getPreferreds(languageFallbackProcessor);
                    }
                }
                exportIndexToFile.exportDefinition(relsFile, concreteRelsFile, args[3], args[4], args[6], descriptions, concepts, args[7], languageFallbackProcessor);

                // Creating stated trans.closure and definition index
                String owlFile = FileHelper.getFile(folder, "rf2-owl-expression", null, "expression", null);
                exportIndexToFile.exportStatedTClosureAndDefinition(owlFile, args[3], args[4], args[6], descriptions, concepts, args[7], languageFallbackProcessor);
            }
        }else if (args[0].contains("-INFERRED_INDEX")){

            if (args.length != 10) {
                throw new Exception("Wrong params number");
            }
            File folder=new File (args[1]);
            if (!folder.exists() || !folder.isDirectory()){
                throw new Exception("Wrong folder param");
            }
            // Creating transitive closure index
            String relsFile=FileHelper.getFile(folder,"rf2-relationships",null,null,"stated");

            ExportIndexToFile exportIndexToFile=new ExportIndexToFile(args);
            exportIndexToFile.exportTClosure(relsFile,args[3],args[4],args[6]);

            // Creating concept definition index
            String concreteRelsFile=FileHelper.getFile(folder,"rf2-inferred-concrete-domains",null,null,"stated");
            String conceptFile=FileHelper.getFile(folder,"rf2-concepts",null,null,"stated");
            String descFile=FileHelper.getFile(folder,"rf2-descriptions",null,null,"stated");

            concepts=null;
            descriptions=null;
            if (conceptFile!=null && descFile!=null){
                loadConceptsAndDescriptions(conceptFile,descFile, args[7]);
            }
            LanguageFallbackProcessor languageFallbackProcessor=null;
            if (args[0].contains("WITH_PREF")) {
                languageFallbackProcessor = getLanguageFallbackProcessor(args[9], folder, descFile);

                if (languageFallbackProcessor!=null && descriptions!=null) {
                    getPreferreds(languageFallbackProcessor);
                }
            }
            exportIndexToFile.exportDefinition(relsFile, concreteRelsFile, args[3], args[4], args[6], descriptions, concepts, args[7], languageFallbackProcessor);

        }else if (args[0].contains("-REFSET_INDEX")){

            if (args.length != 10) {
                throw new Exception("Wrong params number");
            }
            File folder=new File (args[1]);
            if (!folder.exists() || !folder.isDirectory()){
                throw new Exception("Wrong folder param");
            }

            ExportIndexToFile exportIndexToFile=new ExportIndexToFile(args);
            // Creating concept refsets index
            exportIndexToFile.exportRefset(args[1],args[3],args[4],args[6]);

        }else if (args[0].contains("-ALL2MONGO")) {

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
            String concreteRelsFile=FileHelper.getFile(folder,"rf2-inferred-concrete-domains",null,null,"stated");

            DefinitionImporter definitionImporter = new DefinitionImporter();
            definitionImporter.importToMongo(relsFile, concreteRelsFile, args[2], args[3], args[4], args[5], args[6], descFile, conceptFile, args[7]);
            definitionImporter = null;

            // Creating concept refsets index
            RefsetsImporter refsetsImporter = new RefsetsImporter();
            refsetsImporter.importToMongo(args[1],args[2],args[3],args[4],args[5],args[6]);
            refsetsImporter=null;

        }else if (args[0].contains("-DEF2MONGO")) {

//            if (args.length<10){
//                throw new Exception("Wrong params number");
//            }
            TClosureImporter tClosureImporter = new TClosureImporter();
            tClosureImporter.importToMongo(args[1],args[2],args[3],args[4],args[5],args[6]);
            tClosureImporter=null;


//            DefinitionImporter definitionImporter = new DefinitionImporter();
//            definitionImporter.importToMongo(args[1],args[2],args[3],args[4],args[5],args[6], args[7],args[8],args[9]);
//            definitionImporter=null;

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

        long end=new Date().getTime();
        System.out.println("Index to file generation process took: " + (end -start) + " msec.");
    }

    private static void loadConceptsAndDescriptions(String conceptFile, String descFile, String langCode) throws IOException {

        getConceptData(conceptFile);
        getDescData(descFile, langCode);
    }
    private static void getConceptData(String concFile) throws IOException {
        concepts=new HashMap<String, ConceptData>();
        System.out.println("Starting Concepts from: " + concFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(concFile), "UTF8"));
        try {
            String line = br.readLine();
            line = br.readLine(); // Skip header
            int count = 0;
            while (line != null) {
                if (line.trim().equals("")) {
                    line = br.readLine();
                    continue;
                }
                String[] columns = line.split("\\t",-1);
                if ( columns[2].equals("1") ){
                    ConceptData cdata=new ConceptData(columns[3],columns[4].equals("900000000000074008")?"1":"0");
                    concepts.put(columns[0],cdata);

                    count++;
                    if (count % 100000 == 0) {
                        System.out.print(".");
                    }
                }
                line = br.readLine();
            }
            System.out.println(".");
            System.out.println("Concepts loaded = " + concepts.size());
        } finally {
            br.close();
        }
    }

    private static void getDescData(String descFile, String langCode) throws IOException {
        descriptions=new HashMap<String, DescriptionData>();
        System.out.println("Starting Descriptions from: " + descFile);

        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(descFile), "UTF8"));
        String line = br.readLine();
        line = br.readLine(); // Skip header
        int count = 0;
        int pos = 0;
        String semtag;
        while (line != null) {
            if (line.trim().equals("")) {
                line = br.readLine();
                continue;
            }
            String[] columns = line.split("\\t", -1);
            if (columns[2].equals("1") && columns[6].equals("900000000000003001") && concepts.containsKey(columns[4])) {
                if (columns[5].equals(langCode)) {
                    pos = columns[7].lastIndexOf("(");
                    semtag = "";
                    if (pos > 0) {
                        semtag = columns[7].substring(pos + 1, columns[7].lastIndexOf(")"));
                    }
                    DescriptionData dData = new DescriptionData(columns[7], langCode, semtag);
                    descriptions.put(columns[4], dData);
                    count++;
                    if (count % 100000 == 0) {
                        System.out.print(".");
                    }
                } else {
                    DescriptionData dData = descriptions.get(columns[4]);
                    if (dData == null) {
                        pos = columns[7].lastIndexOf("(");
                        semtag = "";
                        if (pos > 0) {
                            semtag = columns[7].substring(pos + 1, columns[7].lastIndexOf(")"));
                        }
                        dData = new DescriptionData(columns[7], langCode, semtag);
                        descriptions.put(columns[4], dData);
                        count++;
                        if (count % 100000 == 0) {
                            System.out.print(".");
                        }
                    }
                }
            }
            line = br.readLine();
        }
        br.close();
        if (count < concepts.size()) {
            System.out.println("Getting more terms from other descriptions");
            HashSet<String> ableToChange = new HashSet<String>();
            br = new BufferedReader(new InputStreamReader(new FileInputStream(descFile), "UTF8"));
            line = br.readLine();
            line = br.readLine(); // Skip header

            while (line != null) {
                if (line.trim().equals("")) {
                    line=br.readLine();
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (concepts.containsKey(columns[4]) && (!descriptions.containsKey(columns[4]) || ableToChange.contains(columns[4]))) {

                    if (columns[2].equals("1")) {
                        semtag = "";
                        if (columns[6].equals("900000000000003001")) {
                            pos = columns[7].lastIndexOf("(");

                            if (pos > 0) {
                                semtag = columns[7].substring(pos + 1, columns[7].lastIndexOf(")"));
                            }
                            DescriptionData dData = new DescriptionData(columns[7], columns[5], semtag);
                            descriptions.put(columns[4], dData);
                            if (ableToChange.contains(columns[4])) {
                                ableToChange.remove(columns[4]);
                            }
                        } else {
                            DescriptionData dData = new DescriptionData(columns[7], columns[5], semtag);
                            descriptions.put(columns[4], dData);
                            ableToChange.add(columns[4]);

                        }
                    }

                    count++;
                    if (count % 100000 == 0) {
                        System.out.print(".");
                    }
                }
                line = br.readLine();
            }
            br.close();
        }
        System.out.println(".");
        System.out.println("Descriptions data loaded = " + descriptions.size());

    }

    private static void getPreferreds(LanguageFallbackProcessor languageFallbackProcessor) throws IOException {
        int count=0;
        for (String conceptId : descriptions.keySet()) {
            DescriptionData descData = descriptions.get(conceptId);
            String term = languageFallbackProcessor.getTerm(Long.parseLong(conceptId));
            if (term != null) {
                descData.setDefaultTerm(term);
            }else{
                count++;
                if (count<10){
                    System.out.println("Sample concepts without pref term:" + conceptId);
                }
            }
        }
        System.out.println("**** INDEX FALLBACK PROCESSOR **** Concepts without term:" + count + " **** Total descriptions:" +  descriptions.size());
    }



    private static LanguageFallbackProcessor getLanguageFallbackProcessor(String arg, File folder, String descFile) throws Exception {
        LanguageFallbackProcessor languageFallbackProcessor=null;
        String deltaLangFile = FileHelper.getFile(folder, "rf2-language", null, "delta", null);
        String snapLangFile = FileHelper.getFile(folder, "rf2-language", null, null, "delta");
        List<String> langFiles= new ArrayList<String>();
        if (snapLangFile != null) {
            langFiles.add(snapLangFile);
        }
        if (deltaLangFile != null) {
            langFiles.add(deltaLangFile);
        }
        FallbackConfig fallbackConfig=new FallbackConfig(arg);
        List<String> descFiles=new ArrayList<String>();
        if (descFile!=null){
            descFiles.add(descFile);
        }
        if (langFiles.size()>0 && descFiles.size()>0) {
            languageFallbackProcessor = new LanguageFallbackProcessor(fallbackConfig, Constants.SYNONYM_TYPE, Constants.PREFERRED_ACCEPTABILITY, descFiles, langFiles);
        }else{
            System.out.println("Cannot process fallback");
            System.out.println("Lang files list count:" + langFiles.size() + ", Desc files list count:" + descFiles.size());
        }
        return languageFallbackProcessor;
    }
}
