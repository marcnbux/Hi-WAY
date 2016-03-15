/*******************************************************************************
 * In the Hi-WAY project we propose a novel approach of executing scientific
 * workflows processing Big Data, as found in NGS applications, on distributed
 * computational infrastructures. The Hi-WAY software stack comprises the func-
 * tional workflow language Cuneiform as well as the Hi-WAY ApplicationMaster
 * for Apache Hadoop 2.x (YARN).
 *
 * List of Contributors:
 *
 * Marc Bux (HU Berlin)
 * Jörgen Brandt (HU Berlin)
 * Hannes Schuh (HU Berlin)
 * Ulf Leser (HU Berlin)
 *
 * Jörgen Brandt is funded by the European Commission through the BiobankCloud
 * project. Marc Bux is funded by the Deutsche Forschungsgemeinschaft through
 * research training group SOAMED (GRK 1651).
 *
 * Copyright 2014 Humboldt-Universität zu Berlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.huberlin.wbi.hiway.am.cuneiforme;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.apache.hadoop.yarn.api.records.ContainerId;

import de.huberlin.wbi.cfjava.cuneiform.Reply;
import de.huberlin.wbi.cfjava.cuneiform.Request;
import de.huberlin.wbi.cfjava.cuneiform.Workflow;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NotDerivableException;
import de.huberlin.wbi.hiway.am.HiWay;
import de.huberlin.wbi.hiway.common.Data;
import de.huberlin.wbi.hiway.common.TaskInstance;

public class CuneiformEApplicationMaster extends HiWay {

	Workflow workflow;
	Set<Request> scheduledRequests;

	public static void main(String[] args) {
		HiWay.loop(new CuneiformEApplicationMaster(), args);
	}

	public CuneiformEApplicationMaster() {
		super();
		setDetermineFileSizes();
		scheduledRequests = new HashSet<>();
	}

	@Override
	public Collection<TaskInstance> parseWorkflow() {
		System.out.println("Parsing Cuneiform workflow " + getWorkflowFile());

		try (BufferedReader reader = new BufferedReader(new FileReader(getWorkflowFile().getLocalPath().toString()))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			workflow = Workflow.createWorkflow(sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		return reduce();
	}

	private Collection<TaskInstance> reduce() {
		Collection<TaskInstance> tasks = new LinkedList<>();
		done = workflow.reduce();
		if (done) {

		}
		Set<Request> requestSet = workflow.getRequestSet();
		requestSet.removeAll(scheduledRequests);
		scheduledRequests.addAll(requestSet);
		for (Request request : requestSet) {
			String taskName = request.getLam().getLamName();
			int taskId = Math.abs(taskName.hashCode() + 1);
			TaskInstance task = new CuneiformETaskInstance(getRunId(), taskName, taskId);

			for (String fileName : request.getStageInFilenameSet()) {
				System.out.println("Input file: " + fileName);
				
				if (!files.containsKey(fileName)) {
					Data file = new Data(fileName);
					file.setInput(true);
					files.put(fileName, file);
				}
				task.addInputData(files.get(fileName));
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(task.getId() + "_request"))) {
				writer.write(request.toString());
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}

			task.setCommand("effi " + task.getId() + "_request " + task.getId() + "_reply");
			tasks.add(task);
		}
		return tasks;
	}

	@Override
	protected Collection<String> getOutput() {
		return workflow.getResult();
	}

	@Override
	public void taskSuccess(TaskInstance task, ContainerId containerId) {
		try {
			(new Data(task.getId() + "_reply", containerId.toString())).stageIn();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(task.getId() + "_reply"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		Reply reply = Reply.createReply(sb.toString());
		
		for (String fileNameString : reply.getStageOutFilenameList()) {
			System.out.println("Output file: " + fileNameString);
			files.put(fileNameString, new Data(fileNameString, containerId.toString()));
		}
		
		workflow.addReply(reply);
		getScheduler().addTasks(reduce());
	}

	@Override
	public void taskFailure(TaskInstance task, ContainerId containerId) {
		super.taskFailure(task, containerId);
	}

}
