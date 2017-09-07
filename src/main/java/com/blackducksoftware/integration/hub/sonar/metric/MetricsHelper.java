/**
 * Black Duck Hub Plugin for SonarQube
 *
 * Copyright (C) 2017 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.sonar.metric;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.ce.measure.Component;
import org.sonar.api.ce.measure.Measure;
import org.sonar.api.ce.measure.MeasureComputer.MeasureComputerContext;
import org.sonar.api.measures.Metric;

import com.blackducksoftware.integration.hub.model.enumeration.RiskCountEnum;
import com.blackducksoftware.integration.hub.model.view.VersionBomComponentView;
import com.blackducksoftware.integration.hub.sonar.HubSonarLogger;
import com.blackducksoftware.integration.hub.sonar.risk.model.RiskProfileModel;

public class MetricsHelper {
    private static final int MAX_COMPONENT_NAME_LENGTH = 20;
    private static final int MAX_COMPONENT_LIST_LENGTH = 140;

    private final SensorContext context;
    private final HubSonarLogger logger;

    public MetricsHelper(final HubSonarLogger logger, final SensorContext context) {
        this.logger = logger;
        this.context = context;
    }

    public void createMeasuresForInputFiles(final Map<String, Set<VersionBomComponentView>> vulnerableComponentsMap, final Iterable<InputFile> inputFiles) {
        for (final InputFile inputFile : inputFiles) {
            createMeasuresForInputFile(vulnerableComponentsMap, inputFile);
        }
    }

    public void createMeasuresForInputFile(final Map<String, Set<VersionBomComponentView>> vulnerableComponentsMap, final InputFile inputFile) {
        final File actualFile = inputFile.file();
        if (actualFile != null) {
            final String[] fileTokens = actualFile.getName().split("/");
            final String fileName = fileTokens[fileTokens.length - 1];
            if (vulnerableComponentsMap.containsKey(fileName)) {
                final StringBuilder compListBuilder = new StringBuilder();
                int numComponents = 0;
                int high = 0;
                int med = 0;
                int low = 0;
                for (final VersionBomComponentView component : vulnerableComponentsMap.get(fileName)) {
                    String compName = component.componentName;
                    if (compName.length() > MAX_COMPONENT_NAME_LENGTH) {
                        compName = compName.substring(0, MAX_COMPONENT_NAME_LENGTH) + "...";
                    }
                    compListBuilder.append(compName + " | ");

                    final RiskProfileModel riskProfile = new RiskProfileModel(component.securityRiskProfile);
                    high += riskProfile.getCountsMap().get(RiskCountEnum.HIGH);
                    med += riskProfile.getCountsMap().get(RiskCountEnum.MEDIUM);
                    low += riskProfile.getCountsMap().get(RiskCountEnum.LOW);
                    numComponents++;
                }
                createMeasure(HubSonarMetrics.NUM_VULN_LOW, inputFile, low);
                createMeasure(HubSonarMetrics.NUM_VULN_MED, inputFile, med);
                createMeasure(HubSonarMetrics.NUM_VULN_HIGH, inputFile, high);
                if ((low + med + high) > 0) {
                    String compList = compListBuilder.toString().substring(0, compListBuilder.length() - 2);
                    if (compList.length() > MAX_COMPONENT_LIST_LENGTH) {
                        compList = compList.substring(0, MAX_COMPONENT_LIST_LENGTH) + "...";
                    }
                    createMeasure(HubSonarMetrics.COMPONENT_NAMES, inputFile, compList.trim());
                }
                createMeasure(HubSonarMetrics.NUM_COMPONENTS, inputFile, numComponents);
            }
        }
    }

    public void createMeasure(@SuppressWarnings("rawtypes") final Metric metric, final InputComponent inputComponent, final Serializable value) {
        logger.debug(String.format("Creating measure: Metric='%s', Component='%s', Value='%s'", metric.getName(), inputComponent, value));
        context.newMeasure().forMetric(metric).on(inputComponent).withValue(value).save();
    }

    public static void computeSecurityVulnerabilityMeasure(final MeasureComputerContext context, final Metric<Integer> metric) {
        if (context.getComponent().getType() != Component.Type.FILE) {
            final String metricKey = metric.getKey();
            int sum = 0;
            for (final Measure child : context.getChildrenMeasures(metricKey)) {
                sum += child.getIntValue();
            }
            context.addMeasure(metricKey, sum);
        }
    }
}