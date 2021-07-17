package com.termmed.util;

public class ConceptData {

    String module;
    String primitive;

    public ConceptData(String module, String primitive) {
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
