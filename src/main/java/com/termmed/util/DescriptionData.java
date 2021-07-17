package com.termmed.util;

public class DescriptionData {

    String defaultTerm;
    String langCode;
    String semTag;

    public DescriptionData(String defaultTerm, String langCode, String semTag) {
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