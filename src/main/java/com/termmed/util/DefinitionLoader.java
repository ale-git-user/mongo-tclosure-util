package com.termmed.util;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.*;
import java.util.*;


public class DefinitionLoader {

	private final LanguageFallbackProcessor languageFallbackProcessor;
	HashMap<String,TreeMap<Integer, TreeMap<String,String>>> definitions;
	String rf2Rels;
	private HashMap<String, ConceptData> concepts;
	private HashMap<String, DescriptionData> descriptions;
	private HashMap<String, HashMap<String,Integer>> typeCounter;

	public DefinitionLoader(String rf2Rels, String concreteRelsFile, String descFile, String concFile, String langCode, LanguageFallbackProcessor languageFallbackProcessor) throws IOException{
		definitions = new HashMap<String, TreeMap<Integer, TreeMap<String, String>>>();
		this.rf2Rels=rf2Rels;
		concepts=new HashMap<String, ConceptData>();
		descriptions=new HashMap<String, DescriptionData>();
		typeCounter=new HashMap<String, HashMap<String, Integer>>();
		this.languageFallbackProcessor=languageFallbackProcessor;
		getConceptData(concFile);
		getDescriptionData(descFile, langCode);

		loadRels(rf2Rels,"inferred");
		loadRels(concreteRelsFile,"concrete");
	}

	public DefinitionLoader( String descFile, String concFile, String langCode, LanguageFallbackProcessor languageFallbackProcessor) throws IOException{
		definitions = new HashMap<String, TreeMap<Integer, TreeMap<String, String>>>();
		concepts=new HashMap<String, ConceptData>();
		descriptions=new HashMap<String, DescriptionData>();
		typeCounter=new HashMap<String, HashMap<String, Integer>>();
		this.languageFallbackProcessor=languageFallbackProcessor;
		getConceptData(concFile);
		getDescriptionData(descFile, langCode);
	}

	public DefinitionLoader(String relsFile, String concreteRelsFile, HashMap<String, DescriptionData> descriptions, HashMap<String, ConceptData> concepts, String langCode, LanguageFallbackProcessor languageFallbackProcessor) throws IOException {
		definitions = new HashMap<String, TreeMap<Integer, TreeMap<String, String>>>();
		this.rf2Rels=rf2Rels;
		this.concepts=concepts;
		this.descriptions=descriptions;
		typeCounter=new HashMap<String, HashMap<String, Integer>>();
		this.languageFallbackProcessor=languageFallbackProcessor;
		loadRels(rf2Rels,"inferred");
		loadRels(concreteRelsFile,"concrete");
	}

	public DefinitionLoader(HashMap<String, DescriptionData> descriptions, HashMap<String, ConceptData> concepts, String langCode, LanguageFallbackProcessor languageFallbackProcessor) {
		definitions = new HashMap<String, TreeMap<Integer, TreeMap<String, String>>>();
		this.concepts=concepts;
		this.descriptions=descriptions;
		typeCounter=new HashMap<String, HashMap<String, Integer>>();
		this.languageFallbackProcessor=languageFallbackProcessor;
	}

	private void getConceptData(String concFile) throws IOException {
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

	private void getDescriptionData(String descFile, String langCode) throws IOException {
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
		if (languageFallbackProcessor!=null) {
			getPreferreds();
		}
		System.out.println(".");
		System.out.println("Descriptions data loaded = " + descriptions.size());

	}

	private void getPreferreds() throws IOException {
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
		System.out.println("**** INDEX FALLBACK PROCESSOR **** Concepts without term:" + count);
	}


	private void loadRels(String rf2Rels, String fileType) throws IOException {
		if (rf2Rels==null || rf2Rels.trim().equals("")){
			System.out.println("Relationships file is null " );
			return;
		}
		File file=new File(rf2Rels);
		if (!file.exists()){
			System.out.println("No relationships file exists: " + rf2Rels);
			return;
		}
		System.out.println("Starting Relationships from: " + rf2Rels);
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));
		try {
			String line = br.readLine();
			line = br.readLine(); // Skip header
			int count = 0;
			String value;
			while (line != null) {
				if (line.trim().equals("")) {
					continue;
				}
				String[] columns = line.split("\\t",-1);
				if ( columns[2].equals("1") ){
					if (fileType.equals("inferred")) {
						addRel(columns[5], columns[4], columns[7], Integer.parseInt(columns[6]));
					}else{
						if (columns[5].length()>0) {
							value = columns[5].substring(1);
							if (columns[5].substring(0,1).equals("\"")) {
								value=value.substring(0,value.length()-1);
							}
						}else{
							value="";
						}
						addRel(value, columns[4], columns[7], Integer.parseInt(columns[6]));
					}
					count++;
					if (count % 100000 == 0) {
						System.out.print(".");
					}
				}
				line = br.readLine();
			}
			System.out.println(".");
			System.out.println("Relationships loaded = " + definitions.size());
		} finally {
			br.close();
		}		
	}
	public void addRel(String dest, String source, String type, Integer groupNr){
		if (dest.equals(source)){
			System.out.println("same destination and source: " + source);
			return;
		}
		HashMap<String, Integer> types = typeCounter.get(source);
		if (types==null){
			types=new HashMap<String,Integer>();
			types.put(type,1);
		}else {
			Integer quantity = types.get(type);
			if (quantity==null){
				quantity=1;
			}else {
				quantity++;
			}
			types.put(type, quantity);
		}
		typeCounter.put(source,types);
		TreeMap<Integer, TreeMap<String, String>> groupsList= definitions.get(source);
		TreeMap<String, String> group;
		if (groupsList==null){
			groupsList= new TreeMap<Integer, TreeMap<String, String>>();
			group = new TreeMap<String, String>();
		}else{
			group=groupsList.get(groupNr);
			if (group==null){
				group = new TreeMap<String, String>();
			}else{
				while (group.containsKey(type)){
					type+="#";
				}
			}
		}
		group.put(type,dest);
		groupsList.put(groupNr,group);
		definitions.put(source, groupsList);
	}
	public void createDumpCollections(String outputFolder, String db, String collectionPrefix, String pathId) throws IOException {

		MetadataGenerator metadataGenerator=new MetadataGenerator(outputFolder + "/" + collectionPrefix + "definition" + pathId + ".metadata.json");
		String metadata=Constants.DEFINITION_METADATA_JSON.replaceAll("===DB===",db);
		metadata=metadata.replaceAll("===COLL===",collectionPrefix);
		metadata=metadata.replaceAll("===PATH===",pathId);
		metadataGenerator.generate(metadata);
		metadataGenerator.close();
		metadataGenerator=null;

		BsonGenerator bsonGenerator=new BsonGenerator(outputFolder + "/" + collectionPrefix + "definition" + pathId + ".bson");
		String module;
		String primitive;
		HashMap<String, Integer> types;
		for (String source : definitions.keySet()) {
			TreeMap<Integer, TreeMap<String, String>> groupsList= definitions.get(source);

			types = typeCounter.get(source);
			List<Document> groupDocs=new ArrayList<Document>();
			for(Integer groupNr:groupsList.keySet()){
				TreeMap<String, String> group = groupsList.get(groupNr);
				List<Document> relDocs=new ArrayList<Document>();
				for (String type:group.keySet()){
					String dest=group.get(type);
					if (type.lastIndexOf("#")>-1){
						type=type.substring(0,type.indexOf("#"));
					}

					Document rDoc=new Document("t",type).append("d",dest).append("q", types.get(type));
					relDocs.add(rDoc);
				}
				groupDocs.add(new Document("rg",relDocs));
			}
			ConceptData cData=concepts.get(source);
			module=null;
			primitive=null;
			if (cData!=null) {
				module = cData.getModule();
				primitive = cData.getPrimitive();
			}
			DescriptionData dData=descriptions.get(source);
			Document doc;
			if (dData!=null) {
				doc = new Document("c", source).append("g", groupDocs).append("m", module).append("p", primitive).append("st", dData.getSemTag()).append("dt", dData.getDefaultTerm());
			}else{
				doc = new Document("c", source).append("g", groupDocs).append("m", module).append("p", primitive);
			}
			bsonGenerator.generate(doc);
		}
		bsonGenerator.close();
	}
	public void toMongo( MongoCollection<Document> collection) throws IOException {
		String module;
		String primitive;
		HashMap<String, Integer> types;
		for (String source : definitions.keySet()) {
			TreeMap<Integer, TreeMap<String, String>> groupsList= definitions.get(source);

			types = typeCounter.get(source);
			List<Document> groupDocs=new ArrayList<Document>();
			for(Integer groupNr:groupsList.keySet()){
				TreeMap<String, String> group = groupsList.get(groupNr);
				List<Document> relDocs=new ArrayList<Document>();
				for (String type:group.keySet()){
					String dest=group.get(type);
					if (type.lastIndexOf("#")>-1){
						type=type.substring(0,type.indexOf("#"));
					}

					Document rDoc=new Document("t",type).append("d",dest).append("q", types.get(type));
					relDocs.add(rDoc);
				}
				groupDocs.add(new Document("rg",relDocs));
			}
			ConceptData cData=concepts.get(source);
			module=null;
			primitive=null;
			if (cData!=null) {
				module = cData.getModule();
				primitive = cData.getPrimitive();
			}
			DescriptionData dData=descriptions.get(source);
			Document doc;
			if (dData!=null) {
				doc = new Document("c", source).append("g", groupDocs).append("m", module).append("p", primitive).append("st", dData.getSemTag()).append("dt", dData.getDefaultTerm());
			}else{
				doc = new Document("c", source).append("g", groupDocs).append("m", module).append("p", primitive);
			}
			collection.insertOne(doc);
		}
	}

}
