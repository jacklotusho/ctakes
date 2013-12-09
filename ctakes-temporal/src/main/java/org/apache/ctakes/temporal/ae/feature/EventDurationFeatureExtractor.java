/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.temporal.ae.feature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ctakes.relationextractor.ae.features.RelationFeaturesExtractor;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.cleartk.classifier.Feature;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

public class EventDurationFeatureExtractor implements RelationFeaturesExtractor {

  @Override
  public List<Feature> extract(JCas jCas, IdentifiedAnnotation arg1, IdentifiedAnnotation arg2)
      throws AnalysisEngineProcessException {

    List<Feature> features = new ArrayList<Feature>();
    File durationLookup = new File("/Users/dima/Boston/Thyme/Duration/Output/Duration/distribution.txt");
    String arg1text = arg1.getCoveredText().toLowerCase();
    String arg2text = arg2.getCoveredText().toLowerCase();
    
    Map<String, Map<String, Float>> textToDistribution = null;
    try {
      textToDistribution = Files.readLines(durationLookup, Charsets.UTF_8, new Callback());
    } catch(IOException e) {
      e.printStackTrace();
      return features;
    }
    
    Map<String, Float> arg1Distribution = textToDistribution.get(arg1text);
    if(arg1Distribution == null) {
      features.add(new Feature("arg1_no_duration_info"));
    } else {
      float expectation1 = ExpectedDurationFeatureExtractor.expectedDuration(arg1Distribution);
      features.add(new Feature("arg1_expected_duration", expectation1));
    }
    
    Map<String, Float> arg2Distribution = textToDistribution.get(arg2text);
    if(arg2Distribution == null) {
      features.add(new Feature("arg2_no_duration_info"));
    } else {
      float expectation2 = ExpectedDurationFeatureExtractor.expectedDuration(arg2Distribution);
      features.add(new Feature("arg2_expected_duration", expectation2));
    }
    
    return features;
  }
  
  private static class Callback implements LineProcessor <Map<String, Map<String, Float>>> {

    // map event text to its duration distribution
    private Map<String, Map<String, Float>> textToDistribution;
    
    public Callback() {
      textToDistribution = new HashMap<String, Map<String, Float>>();
    }
    
    public boolean processLine(String line) throws IOException {

      String[] elements = line.split(", "); // e.g. pain, second:0.000, minute:0.005, hour:0.099, ...
      Map<String, Float> distribution = new HashMap<String, Float>();
      
      for(int durationBinNumber = 1; durationBinNumber < elements.length; durationBinNumber++) {
        String[] durationAndValue = elements[durationBinNumber].split(":"); // e.g. "day:0.475"
        distribution.put(durationAndValue[0], Float.parseFloat(durationAndValue[1]));
      }
      
      textToDistribution.put(elements[0], distribution);
      return true;
    }

    public Map<String, Map<String, Float>> getResult() {

      return textToDistribution;
    }
  }
}