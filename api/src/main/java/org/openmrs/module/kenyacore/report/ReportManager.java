/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.kenyacore.report;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Program;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.AppDescriptor;
import org.openmrs.module.kenyacore.ContentManager;
import org.openmrs.module.kenyacore.program.ProgramDescriptor;
import org.openmrs.module.kenyacore.program.ProgramManager;
import org.openmrs.module.kenyacore.report.builder.Builds;
import org.openmrs.module.kenyacore.report.builder.CalculationReportBuilder;
import org.openmrs.module.kenyacore.report.builder.ReportBuilder;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Report manager
 */
@Component
public class ReportManager implements ContentManager {

	protected static final Log log = LogFactory.getLog(ReportManager.class);

	private Map<String, ReportDescriptor> reports = new LinkedHashMap<String, ReportDescriptor>();

	private List<ReportDescriptor> commonReports = new ArrayList<ReportDescriptor>();

	private Map<String, ReportBuilder> builders = new HashMap<String, ReportBuilder>();

	@Autowired
	private ProgramManager programManager;

	@Autowired
	@Qualifier("kenyacore.genericCalcReportBuilder")
	private CalculationReportBuilder calculationReportBuilder;

	private Map<ReportDescriptor, ReportDefinition> definitionCache = new HashMap<ReportDescriptor, ReportDefinition>();

	/**
	 * @see org.openmrs.module.kenyacore.ContentManager#getPriority()
	 */
	@Override
	public int getPriority() {
		return 80;
	}

	/**
	 * @see org.openmrs.module.kenyacore.ContentManager#refresh()
	 */
	@Override
	public synchronized void refresh() {
		reports.clear();

		// Process report descriptor components
		for (ReportDescriptor descriptor : Context.getRegisteredComponents(ReportDescriptor.class)) {
			reports.put(descriptor.getId(), descriptor);

			log.debug("Registered report descriptor: " + descriptor.getId());
		}

		// Process form configuration beans
		for (ReportConfiguration configuration : Context.getRegisteredComponents(ReportConfiguration.class)) {
			// Register common reports
			if (configuration.getCommonReports() != null) {
				commonReports.addAll(configuration.getCommonReports());
			}

			// Register additional program specific reports
			if (configuration.getProgramReports() != null) {
				Map<ProgramDescriptor, Set<ReportDescriptor>> programReports = configuration.getProgramReports();

				for (ProgramDescriptor programDescriptor : programReports.keySet()) {
					for (ReportDescriptor report : programReports.get(programDescriptor)) {
						programDescriptor.addReport(report);
					}
				}
			}
		}

		// Process report builder components
		for (ReportBuilder builder : Context.getRegisteredComponents(ReportBuilder.class)) {
			Builds builds = builder.getClass().getAnnotation(Builds.class);
			if (builds != null) {
				builders.put(builds.value(), builder);
			}
		}
	}

	/**
	 * Gets a report descriptor by id
	 * @param id the descriptor id
	 * @return the report descriptor
	 */
	public ReportDescriptor getReportDescriptor(String id) {
		return reports.get(id);
	}

	/**
	 * Gets all report descriptors
	 * @@return the list of report descriptors
	 */
	public List<ReportDescriptor> getAllReportDescriptors() {
		return new ArrayList<ReportDescriptor>(reports.values());
	}

	/**
	 * Gets all common (non program specific) reports
	 * @@return the list of reports
	 */
	public List<ReportDescriptor> getCommonReports(AppDescriptor app) {
		return filterReports(commonReports, app);
	}

	/**
	 * Gets program specific reports
	 * @@return the list of reports
	 */
	public List<ReportDescriptor> getProgramReports(AppDescriptor app, Program program) {
		ProgramDescriptor programDescriptor = programManager.getProgramDescriptor(program);
		if (programDescriptor.getReports() != null) {
			return filterReports(programDescriptor.getReports(), app);
		}
		return Collections.emptyList();
	}

	/**
	 * Gets a report definition for the given report
	 * @param report the report
	 * @return the report definition
	 */
	public ReportDefinition getReportDefinition(ReportDescriptor report) {
		if (definitionCache.containsKey(report)) {
			return definitionCache.get(report);
		}

		// Look for specific builder
		ReportBuilder builder = builders.get(report.getId());

		// Can we use the generic calculation report builder?
		if (builder == null && report instanceof CalculationReportDescriptor) {
			builder = calculationReportBuilder;
		}

		if (builder == null) {
			throw new RuntimeException("No suitable report builder component found");
		}

		ReportDefinition definition = builder.getDefinition(report);
		definitionCache.put(report, definition);
		return definition;
	}

	/**
	 * Filters the given collection of reports to those applicable for the given application
	 * @param app the application
	 * @return the filtered reports
	 */
	protected List<ReportDescriptor> filterReports(Collection<ReportDescriptor> descriptors, AppDescriptor app) {
		List<ReportDescriptor> filtered = new ArrayList<ReportDescriptor>();
		for (ReportDescriptor descriptor : descriptors) {
			// Filter by app id
			if (app != null && !descriptor.getApps().contains(app)) {
				continue;
			}

			filtered.add(descriptor);
		}

		return filtered;
	}
}