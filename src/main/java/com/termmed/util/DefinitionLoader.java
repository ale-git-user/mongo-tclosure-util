package com.termmed.util;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.*;
import java.util.*;


public class DefinitionLoader {

	HashMap<String,TreeMap<Integer, TreeMap<String,String>>> definitions;
	private String ISARELATIONSHIPTYPEID="116680003";
	String rf2Rels;
	private HashMap<String, ConcData> concepts;
	private HashMap<String, DescData> descriptions;
	private HashMap<String, HashMap<String,Integer>> typeCounter;

	public DefinitionLoader(String rf2Rels, String descFile, String concFile, String langCode) throws IOException{
		definitions = new HashMap<String, TreeMap<Integer, TreeMap<String, String>>>();
		this.rf2Rels=rf2Rels;
		concepts=new HashMap<String, ConcData>();
		descriptions=new HashMap<String, DescData>();
		typeCounter=new HashMap<String, HashMap<String, Integer>>();
		getConceptData(concFile);
		getDescData(descFile, langCode);

		loadNoIsas();
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
					continue;
				}
				String[] columns = line.split("\\t",-1);
				if ( columns[2].equals("1") ){
					ConcData cdata=new ConcData(columns[3],columns[4].equals("900000000000074008")?"1":"0");
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

	private void getDescData(String descFile, String langCode) throws IOException {

		System.out.println("Starting Descriptions from: " + descFile);

			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(descFile), "UTF8"));
			String line = br.readLine();
			line = br.readLine(); // Skip header
			int count = 0;
			int pos=0;
			String semtag;
			HashSet<String> hControl = new HashSet<String>();
			while (line != null) {
				if (line.trim().equals("")) {
					continue;
				}
				String[] columns = line.split("\\t",-1);
				if ( columns[2].equals("1") && columns[6].equals("900000000000003001") && columns[5].equals(langCode) && concepts.containsKey(columns[4])){
					pos=columns[7].lastIndexOf("(");
					semtag="";
					if (pos>0){

						semtag=columns[7].substring(pos +1, columns[7].lastIndexOf(")"));
					}
					DescData dData=new DescData(columns[7],langCode, semtag);
					descriptions.put(columns[4],dData);
					count++;
					if (count % 100000 == 0) {
						System.out.print(".");
					}

				}
				line = br.readLine();
			}
			br.close();
			if (count<concepts.size()) {
				System.out.println("Getting more terms from other descriptions");
				HashSet<String> ableToChange = new HashSet<String>();
				br = new BufferedReader(new InputStreamReader(new FileInputStream(descFile), "UTF8"));
				line = br.readLine();
				line = br.readLine(); // Skip header

				while (line != null) {
					if (line.trim().equals("")) {
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
								DescData dData = new DescData(columns[7], columns[5], semtag);
								descriptions.put(columns[4], dData);
								if (ableToChange.contains(columns[4])){
									ableToChange.remove(columns[4]);
								}
							} else {
								DescData dData = new DescData(columns[7], columns[5], semtag);
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

	private void loadNoIsas() throws IOException {
		System.out.println("Starting Relationships from: " + rf2Rels);
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(rf2Rels), "UTF8"));
		try {
			String line = br.readLine();
			line = br.readLine(); // Skip header
			int count = 0;
			while (line != null) {
				if (line.trim().equals("")) {
					continue;
				}
				String[] columns = line.split("\\t",-1);
				if ( columns[2].equals("1") ){
					addRel(columns[5],columns[4],columns[7],Integer.parseInt(columns[6]));

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
			ConcData cData=concepts.get(source);
			module=null;
			primitive=null;
			if (cData!=null) {
				module = cData.getModule();
				primitive = cData.getPrimitive();
			}
			DescData dData=descriptions.get(source);
			Document doc;
			if (dData!=null) {
				doc = new Document("c", source).append("g", groupDocs).append("m", module).append("p", primitive).append("st", dData.getSemTag()).append("dt", dData.getDefaultTerm());
			}else{
				doc = new Document("c", source).append("g", groupDocs).append("m", module).append("p", primitive);
			}
			collection.insertOne(doc);
		}
	}

	private class ConcData{
		String module;
		String primitive;

		public ConcData(String module, String primitive) {
			this.module = module;
			this.primitive = primitive;
		}

		public String getModule() {
			return module;
		}

		public void setModule(String module) {
			this.module = module;
		}

		public String getPrimitive() {
			return primitive;
		}

		public void setPrimitive(String primitive) {
			this.primitive = primitive;
		}
	}
	private class DescData {
		String defaultTerm;
		String langCode;
		String semTag;

		public DescData(String defaultTerm, String langCode, String semTag) {
			this.defaultTerm = defaultTerm;
			this.langCode = langCode;
			this.semTag = semTag;
		}

		public String getDefaultTerm() {
			return defaultTerm;
		}

		public void setDefaultTerm(String defaultTerm) {
			this.defaultTerm = defaultTerm;
		}

		public String getLangCode() {
			return langCode;
		}

		public void setLangCode(String langCode) {
			this.langCode = langCode;
		}

		public String getSemTag() {
			return semTag;
		}

		public void setSemTag(String semTag) {
			this.semTag = semTag;
		}
	}
}
