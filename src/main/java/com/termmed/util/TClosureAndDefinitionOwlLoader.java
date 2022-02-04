package com.termmed.util;

import com.google.common.collect.Sets;
import com.sun.org.apache.bcel.internal.classfile.ConstantValue;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.snomed.otf.owltoolkit.constants.Concepts;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TClosureAndDefinitionOwlLoader {
    private final AxiomRelationshipConversionService axiomRelationshipConversionService;
    private final DefinitionLoader definitionLoader;
    TClosure tClos;
    File owlFile;
    public TClosureAndDefinitionOwlLoader(TClosure tClos, DefinitionLoader definitionLoader, String owlFilename) throws Exception {
        this.tClos = tClos;
        this.owlFile=new File(owlFilename);
        if (!owlFile.exists()){
            throw new Exception("Owl file doesn't exist.");
        }
        axiomRelationshipConversionService = new AxiomRelationshipConversionService(Sets.newHashSet(Concepts.LATERALITY_LONG));
        this.definitionLoader=definitionLoader;

    }
    public void load() throws Exception {
        int countIsas=0;
        int countDefs=0;
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(owlFile), "UTF8"));
        try {
            String line = br.readLine();
            line = br.readLine(); // Skip header
            int count = 0;
            while (line != null) {
                if (line.equals("")) {
                    line = br.readLine();
                    continue;
                }
                String[] columns = line.split("\t", -1);

                if (columns[2].equals("0") || !columns[4].equals(Constants.AXIOM_REFSET)) {
                    line = br.readLine();
                    continue;
                }
                String sourceId = columns[5];

                OWLAxiom owlAxiom = null;
                try {
                    owlAxiom = axiomRelationshipConversionService.convertOwlExpressionToOWLAxiom(columns[6]);

                    AxiomRepresentation axiomRepresentation = axiomRelationshipConversionService.convertAxiomToRelationships(owlAxiom);
                    if (axiomRepresentation == null) {
                        line = br.readLine();
                        continue;
                    }
                    boolean invertedIsa = false;
                    String invertedTarget = "";
                    if (!(axiomRepresentation.getLeftHandSideNamedConcept() != null &&
                            axiomRepresentation.getLeftHandSideNamedConcept().equals(Long.parseLong(sourceId))) &&
                            !(axiomRepresentation.getRightHandSideNamedConcept() != null &&
                                    axiomRepresentation.getRightHandSideNamedConcept().equals(Long.parseLong(sourceId)))) {

                        if (axiomRepresentation.getRightHandSideRelationships() != null &&
                                axiomRepresentation.getRightHandSideRelationships().size() > 0) {

                            for (Integer group : axiomRepresentation.getRightHandSideRelationships().keySet()) {
                                List<Relationship> axiomRelationships = axiomRepresentation.getRightHandSideRelationships().get(group);
                                for (Relationship axiomRelationship : axiomRelationships) {
                                    if (String.valueOf(axiomRelationship.getDestinationId()).equals(sourceId) &&
                                            axiomRepresentation.getLeftHandSideNamedConcept() != null &&
                                            !axiomRepresentation.getLeftHandSideNamedConcept().equals(sourceId)) {
//                                            invertedTarget=String.valueOf(axiomRepresentation.getLeftHandSideNamedConcept());
                                        invertedIsa = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!invertedIsa) {
                            line = br.readLine();
                            continue;
                        }
                    }
                    Map<Integer, List<Relationship>> axiomRelationshipGroups = null;
                    if (axiomRepresentation.getRightHandSideRelationships() != null &&
                            axiomRepresentation.getRightHandSideRelationships().size() > 0) {
                        axiomRelationshipGroups = axiomRepresentation.getRightHandSideRelationships();
                    }
                    if (axiomRepresentation.getLeftHandSideRelationships() != null &&
                            axiomRepresentation.getLeftHandSideRelationships().size() > 0) {
                        axiomRelationshipGroups = axiomRepresentation.getLeftHandSideRelationships();
                    }
                    if (axiomRelationshipGroups != null) {

                        for (Integer group : axiomRelationshipGroups.keySet()) {
                            List<Relationship> axiomRelationships = axiomRelationshipGroups.get(group);
                            for (Relationship axiomRelationship : axiomRelationships) {
                                Relationship.ConcreteValue concreteValue = axiomRelationship.getValue();
                                if (invertedIsa && String.valueOf(axiomRelationship.getDestinationId()).equals(sourceId)) {
                                    invertedTarget = String.valueOf(axiomRepresentation.getLeftHandSideNamedConcept());
                                    if (Concepts.IS_A_LONG.equals(axiomRelationship.getTypeId())) {
                                        tClos.addRel(invertedTarget, sourceId);
                                        countIsas++;
                                    }else{
                                        countDefs++;
                                    }

                                    if (concreteValue != null) {
                                        definitionLoader.addRel(concreteValue.asString(), sourceId, String.valueOf(axiomRelationship.getTypeId()), group);
                                    } else {
                                        definitionLoader.addRel(invertedTarget, sourceId, String.valueOf(axiomRelationship.getTypeId()), group);
                                    }

                                } else {
                                    if (Concepts.IS_A_LONG.equals(axiomRelationship.getTypeId())) {
                                        tClos.addRel(String.valueOf(axiomRelationship.getDestinationId()), sourceId);
                                        countIsas++;
                                    }else{
                                        countDefs++;
                                    }
                                    if (concreteValue != null) {
                                        definitionLoader.addRel(concreteValue.asString(), sourceId, String.valueOf(axiomRelationship.getTypeId()), group);
                                    } else {
                                        definitionLoader.addRel(String.valueOf(axiomRelationship.getDestinationId()), sourceId, String.valueOf(axiomRelationship.getTypeId()), group);
                                    }
                                }
                            }
                        }
                    }
                } catch (ConversionException e) {
                    e.printStackTrace();
                }

                line = br.readLine();
                count++;
                if (count % 100000 == 0) {
                    System.out.print(".");
                }
            }

            System.out.println(countIsas + " Isas and " + countDefs + " Defining rels from owl relationships loaded from file " + owlFile.getName());
        } finally {
            br.close();
        }
    }
}
