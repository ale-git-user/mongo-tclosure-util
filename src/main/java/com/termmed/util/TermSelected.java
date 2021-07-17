package com.termmed.util;

public class TermSelected {

    Integer priority;
    Integer effTime;
    Integer active;
    Long descriptionId;

    public TermSelected(Long descriptionId, Integer priority, Integer effTime, Integer active) {
        this.descriptionId=descriptionId;
        this.priority = priority;
        this.effTime = effTime;
        this.active=active;
    }

    public Long getDescriptionId() {
        return descriptionId;
    }

    public void setDescriptionId(Long descriptionId) {
        this.descriptionId = descriptionId;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getEffTime() {
        return effTime;
    }

    public void setEffTime(Integer effTime) {
        this.effTime = effTime;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }
}
