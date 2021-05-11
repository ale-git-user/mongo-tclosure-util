package com.termmed.util;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;


public class TClosure {

	HashMap<String,HashSet<String>>parentHier;
	HashMap<String,HashSet<String>>childrenHier;
	private String ISARELATIONSHIPTYPEID="116680003";
	private String ROOT_CONCEPT = "138875005";
	String rf2Rels;
	private HashSet<String> hControl;

	public TClosure(String rf2Rels) throws IOException{
		parentHier=new HashMap<String,HashSet<String>>();
		childrenHier=new HashMap<String,HashSet<String>>();
		this.rf2Rels=rf2Rels;
		loadIsas();
	}

	private void loadIsas() throws IOException {
		System.out.println("Starting Isas Relationships from: " + rf2Rels);
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(rf2Rels), "UTF8"));
		try {
			String line = br.readLine();
			line = br.readLine(); // Skip header
			int count = 0;
			while (line != null) {
				if (line.trim().equals("")) {
					continue;
				}
				String[] columns = line.split("\\t");
				if (columns[7].equals(ISARELATIONSHIPTYPEID)
						&& columns[2].equals("1") 
						&& !columns[4].equals(ROOT_CONCEPT)){
					addRel(columns[5],columns[4]);

					count++;
					if (count % 100000 == 0) {
						System.out.print(".");
					}
				}
				line = br.readLine();
			}
			System.out.println(".");
			System.out.println("Parent isas Relationships loaded = " + parentHier.size());
			System.out.println("Children isas Relationships loaded = " + childrenHier.size());
		} finally {
			br.close();
		}		
	}
	public void addRel(String parent, String child){
		if (parent.equals(child)){
			System.out.println("same child and parent: " + child);
			return;
		}
		HashSet<String> parentList=parentHier.get(child);
		if (parentList==null){
			parentList=new HashSet<String>();
		}
		parentList.add(parent);
		parentHier.put(child, parentList);

		HashSet<String> childrenList=childrenHier.get(parent);
		if (childrenList==null){
			childrenList=new HashSet<String>();
		}
		childrenList.add(child);
		childrenHier.put(parent, childrenList);
	}

	public boolean isAncestorOf(String ancestor,String descendant){

		HashSet<String>parent=parentHier.get(descendant);
		if (parent==null){
			return false;
		}
		if (parent.contains(ancestor)){
			return true;
		}
		for(String par:parent){
			if (isAncestorOf(ancestor,par)){
				return true;
			}
		}
		return false;
	}

	public HashSet<String> getParent(String conceptId) {
		return parentHier.get(conceptId);
	}

	public HashSet<String> getChildren(String conceptId) {
		return childrenHier.get(conceptId);
	}

	public void toFile(String outputFile) throws IOException{
		BufferedWriter bw = getWriter(outputFile);
		addTClosureFileHeader(bw);
		writeHierarchy(bw);
		bw.close();
	}

	private void addTClosureFileHeader(BufferedWriter bw) throws IOException {
		bw.append("descendant");
		bw.append("\t");
		bw.append("ancestor");
		bw.append("\r\n");		
	}

	private BufferedWriter getWriter(String outFile) throws UnsupportedEncodingException, FileNotFoundException {

		FileOutputStream tfos = new FileOutputStream( outFile);
		OutputStreamWriter tfosw = new OutputStreamWriter(tfos,"UTF-8");
		return new BufferedWriter(tfosw);

	}

	private void writeHierarchy(BufferedWriter bw) throws IOException{

		for (String child: parentHier.keySet()){
			hControl=new HashSet<String>();
			writeParents(bw,child,child);
			hControl=null;
		}

	}

	private void writeParents(BufferedWriter bw, String child,String descendant) throws IOException {

		HashSet<String> parents=parentHier.get(child);
		if (parents==null){
			return;
		}

		for(String par:parents){
			if (!hControl.contains(par)){
				hControl.add(par);
			bw.append(descendant.toString());
			bw.append("\t");
			bw.append(par.toString());
			bw.append("\r\n");
			writeParents(bw, par,descendant);
			}
		}		
	}

	public void toMongo(MongoCollection<Document> descCollection, MongoCollection<Document> ancesCollection) throws IOException {

		for (String child : parentHier.keySet()) {
			hControl = new HashSet<String>();
			getParentList( child);
			ancesCollection.insertOne(new Document("c",child.toString()).append("a", hControl));
			hControl = null;
		}
		childrenHier.remove(Long.parseLong(ROOT_CONCEPT));
		for (String parent : childrenHier.keySet()) {
			hControl = new HashSet<String>();
			getChildrenList( parent);
			descCollection.insertOne(new Document("c",parent.toString()).append("d", hControl));
			hControl = null;
		}
	}
	private void getParentList( String child) throws IOException {

		HashSet<String> parents=parentHier.get(child);
		if (parents==null){
			return;
		}

		for(String par:parents){
			if (!hControl.contains(par)){
				hControl.add(par);
				getParentList( par);
			}
		}
	}
	private void getChildrenList( String parent) throws IOException {

		HashSet<String> children=childrenHier.get(parent);
		if (children==null){
			return;
		}

		for(String child:children){
			if (!hControl.contains(child)){
				hControl.add(child);
				getChildrenList( child);
			}
		}
	}
}
