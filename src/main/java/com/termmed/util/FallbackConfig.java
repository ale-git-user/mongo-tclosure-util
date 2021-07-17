package com.termmed.util;

import java.util.HashMap;
import java.util.TreeMap;

public class FallbackConfig {
    TreeMap<Integer,Long> tMap =new TreeMap();
    HashMap<Long,Integer> langMap=new HashMap();
    public FallbackConfig(TreeMap<Integer, Long> tMap) {
        this.tMap = tMap;
        loadMap();
    }

    public FallbackConfig(String langRefsets) {
        String[] arrLangRefsets=langRefsets.split("---",-1);
        for(int i=0;i<arrLangRefsets.length;i++) {
            tMap.put(i, Long.parseLong(arrLangRefsets[i]));
        }
        loadMap();
    }
    private void loadMap(){
        for(Integer key: tMap.keySet()){
            langMap.put(tMap.get(key),key);
        }
    }
    public Integer getLangPriority(Long langRefset){
        return langMap.get(langRefset);
    }
    public Long getRefsetIdFromPriority(Integer priority){
        return tMap.get(priority);
    }
}
