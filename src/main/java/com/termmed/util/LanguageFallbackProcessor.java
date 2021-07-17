package com.termmed.util;

import java.io.*;
import java.util.*;

public class LanguageFallbackProcessor {

    private final Long acceptabilityIdFilter;
    private final List<String> descFiles;
    private final List<String> langFiles;
    private final String termType;
    FallbackConfig fallbackConfig;
    HashMap<Long, TreeMap<Integer, List<TermSelected>>> terms;
    private HashMap<Long, Long> descIdsCandidates;
    private HashMap<Long, String> descTermsCandidates;

    public LanguageFallbackProcessor(FallbackConfig fallbackConfig, String termType, Long acceptabilityIdFilter, List<String> descFiles, List<String> langFiles) throws IOException {
        this.fallbackConfig = fallbackConfig;
        this.terms = new HashMap<Long, TreeMap<Integer, List<TermSelected>>>();
        this.termType = termType;
        this.acceptabilityIdFilter = acceptabilityIdFilter;
        this.descFiles = descFiles;
        this.langFiles = langFiles;
        getCandidates();
        getFallback();
    }

    private void getCandidates() throws IOException {
        descIdsCandidates = new HashMap<Long, Long>();
        descTermsCandidates = new HashMap<Long, String>();

        for (String descFile : descFiles) {
            System.out.println("Starting Descriptions from: " + descFile);

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(descFile), "UTF8"));
            String line = br.readLine();  // Skip header
            String[] columns;
            while ((line = br.readLine()) != null) {
                if (line.trim().equals("")) {
                    continue;
                }
                columns = line.split("\\t", -1);
                if (columns[2].equals("1") && columns[6].equals(termType)) {
                    descIdsCandidates.put(Long.parseLong(columns[0]), Long.parseLong(columns[4]));
                    descTermsCandidates.put(Long.parseLong(columns[0]), columns[7]);
                }
            }
            br.close();
        }
    }

    private void getFallback() throws IOException {
        for (String langFilePath : langFiles) {
            File langFile = new File(langFilePath);
            System.out.println("Starting Language from: " + langFilePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(langFilePath), "UTF8"));
            String line = br.readLine(); // Skip header
            int count = 0;
            int pos = 0;
            String[] spl;

            while ((line = br.readLine()) != null) {
                if (line.trim().equals("")) {
                    continue;
                }
                spl = line.split("\t", -1);
                Long descId = Long.parseLong(spl[5]);
                if (descIdsCandidates.containsKey(descId)) {
                    processFallback(descIdsCandidates.get(descId), Integer.parseInt(spl[1]),
                            Integer.parseInt(spl[2]), Long.parseLong(spl[4]), descId, Long.parseLong(spl[6]));
                }
            }
            br.close();
        }
    }

    public String getTerm(Long conceptId) {
        String term = null;
        TermSelected termSelected = getTermSelected(conceptId);
        if (termSelected != null) {
            term = descTermsCandidates.get(termSelected.getDescriptionId());
        }
        return term;
    }

    public void addLangRefsetLine(Long conceptId, String line) {
        String[] spl = line.split("\t", -1);

        processFallback(conceptId, Integer.parseInt(spl[1]), Integer.parseInt(spl[2]), Long.parseLong(spl[4]), Long.parseLong(spl[5]), Long.parseLong(spl[6]));
    }

    public void processFallback(Long conceptId, Integer effTime, Integer active, Long refsetId, Long descriptionId, Long acceptabilityId) {

        Integer priority = fallbackConfig.getLangPriority(refsetId);
        if (priority == null) {
            return;
        }
        TreeMap<Integer, List<TermSelected>> termsSelected = terms.get(conceptId);
        TermSelected termSelected = null;
        List<TermSelected> termsInPriority = null;
        if (termsSelected == null) {
            termsSelected=new TreeMap<Integer, List<TermSelected>>();
            termsInPriority = new ArrayList<TermSelected>();
            setTermInRefsets(effTime, active, descriptionId, acceptabilityId, priority, termsSelected, termsInPriority);
            termsSelected.put(priority, termsInPriority);
            terms.put(conceptId,termsSelected);
        } else {
            termsInPriority = termsSelected.get(priority);
            if (termsInPriority == null) {
                termsInPriority = new ArrayList<TermSelected>();
                setTermInRefsets(effTime, active, descriptionId, acceptabilityId, priority, termsSelected, termsInPriority);
                termsSelected.put(priority, termsInPriority);
            } else {
                boolean found = false;
                for (TermSelected termSelected1 : termsInPriority) {
                    if (termSelected1.getDescriptionId().equals(descriptionId)) {
                        found = true;
                        if (effTime > termSelected1.getEffTime()) {
//                            termSelected1.setPriority(priority);
                            termSelected1.setEffTime(effTime);
//                            termSelected1.setDescriptionId(descriptionId);
                            if (acceptabilityIdFilter.equals(acceptabilityId)) {
                                termSelected1.setActive(active);
                            } else {
                                termSelected1.setActive(0);
                            }
                            if (active == 0 || !acceptabilityIdFilter.equals(acceptabilityId)) {
                                setInactiveInLowerPriorityRefsets(termsSelected, priority, descriptionId, acceptabilityId, active);
                            }
                        }
                        break;
                    }
                }
                if (!found) {
                    setTermInRefsets(effTime, active, descriptionId, acceptabilityId, priority, termsSelected, termsInPriority);
//                    termsSelected.put(priority,termsInPriority);
                }
            }
        }
    }

    private void setTermInRefsets(Integer effTime, Integer active, Long descriptionId, Long acceptabilityId, Integer priority, TreeMap<Integer, List<TermSelected>> termsSelected, List<TermSelected> termsInPriority) {
        TermSelected termSelected;
        if (acceptabilityIdFilter.equals(acceptabilityId)) {
            termSelected = new TermSelected(descriptionId, priority, effTime, active);
        } else {
            termSelected = new TermSelected(descriptionId, priority, effTime, 0);
        }
        if (active == 0 || !acceptabilityIdFilter.equals(acceptabilityId)) {
            setInactiveInLowerPriorityRefsets(termsSelected, priority, descriptionId, acceptabilityId, active);
        }
        termsInPriority.add(termSelected);
    }

    private void setInactiveInLowerPriorityRefsets(TreeMap<Integer, List<TermSelected>> termsSelected, Integer priority, Long descriptionId, Long acceptabilityId, Integer active) {
        for (Integer langPriority : termsSelected.keySet()) {
            if (langPriority > priority) {
                List<TermSelected> termsList = termsSelected.get(langPriority);
                for (TermSelected termSelected : termsList) {
                    if (termSelected.getDescriptionId().equals(descriptionId)) {
                        termSelected.setActive(0);
                    }
                }
            }
        }
    }

    public TermSelected getTermSelected(Long conceptId) {
        TreeMap<Integer, List<TermSelected>> termsSelected = terms.get(conceptId);
        if (termsSelected != null) {
            for (Integer priority : termsSelected.keySet()) {
                List<TermSelected> termsInPriority = termsSelected.get(priority);
                if (termsInPriority != null) {
                    for (TermSelected termSelected : termsInPriority) {
                        if (termSelected.getActive() == 1) {
                            return termSelected;
                        }
                    }
                }
            }
        }
        return null;
    }

//
//    private void resolveChange(Integer effTime, Integer active, Long descriptionId, Long acceptabilityId, TermSelected termSelected, Integer priority) {
//        if (active==1) {
//            if (acceptabilityIdFilter.equals(acceptabilityId)) {
//                termSelected.setPriority(priority);
//                termSelected.setEffTime(effTime);
//                termSelected.setDescriptionId(descriptionId);
//            } else if (descriptionId.equals(termSelected.getDescriptionId())) {
//                termSelected = null;
//            }
//        } else if (descriptionId.equals(termSelected.getDescriptionId())) {
//            termSelected = null;
//        }
//    }
}
