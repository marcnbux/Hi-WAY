/*******************************************************************************
 * In the Hi-WAY project we propose a novel approach of executing scientific
 * workflows processing Big Data, as found in NGS applications, on distributed
 * computational infrastructures. The Hi-WAY software stack comprises the func-
 * tional workflow language Cuneiform as well as the Hi-WAY ApplicationMaster
 * for Apache Hadoop 2.x (YARN).
 *
 * List of Contributors:
 *
 * Jörgen Brandt (HU Berlin)
 * Marc Bux (HU Berlin)
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
package de.huberlin.wbi.hiway.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

import de.huberlin.wbi.cuneiform.core.invoc.Invocation;
import de.huberlin.wbi.cuneiform.core.semanticmodel.JsonReportEntry;
import de.huberlin.wbi.hiway.common.Constant;
import de.huberlin.wbi.hiway.common.Data;

public class Worker {

	public static void main(String[] args) {
		Worker worker = new Worker();
		try {
			worker.init(args);
			worker.run();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String appId;
	private String containerId;
	private Path dir;
	private String langLabel;
	private long signature;
	private long taskId;
	private String taskName;

	private UUID workflowId;

	public Worker() {
	}

	private void exec() {
		List<String> commands = new LinkedList<>();
		commands.add("/usr/bin/time");
		commands.add("-a");
		commands.add("-o");
		commands.add(Invocation.REPORT_FILENAME);
		commands.add("-f");
		commands.add("{" + JsonReportEntry.ATT_TIMESTAMP + ":"
				+ System.currentTimeMillis() + "," + JsonReportEntry.ATT_RUNID
				+ ":\"" + workflowId + "\"," + JsonReportEntry.ATT_TASKID + ":"
				+ taskId + "," + JsonReportEntry.ATT_TASKNAME + ":\""
				+ taskName + "\"," + JsonReportEntry.ATT_LANG + ":\""
				+ langLabel + "\"," + JsonReportEntry.ATT_INVOCID + ":"
				+ signature + "," + JsonReportEntry.ATT_KEY + ":\""
				+ JsonReportEntry.KEY_INVOC_TIME + "\","
				+ JsonReportEntry.ATT_VALUE + ":"
				+ "{\"realTime\":%e,\"userTime\":%U,\"sysTime\":%S,"
				+ "\"maxResidentSetSize\":%M,\"avgResidentSetSize\":%t,"
				+ "\"avgDataSize\":%D,\"avgStackSize\":%p,\"avgTextSize\":%X,"
				+ "\"nMajPageFault\":%F,\"nMinPageFault\":%R,"
				+ "\"nSwapOutMainMem\":%W,\"nForcedContextSwitch\":%c,"
				+ "\"nWaitContextSwitch\":%w,\"nIoRead\":%I,\"nIoWrite\":%O,"
				+ "\"nSocketRead\":%r,\"nSocketWrite\":%s,\"nSignal\":%k}}");
		commands.add("./" + containerId + ".sh");

		ProcessBuilder processBuilder = new ProcessBuilder(commands);
		processBuilder.directory(dir.toFile());
		Process process;
		int exitValue = -1;
		try {
			// File stdInFile = new File("__stdin__.txt");
			File stdOutFile = new File(Invocation.STDOUT_FILENAME);
			File stdErrFile = new File(Invocation.STDERR_FILENAME);
			// stdInFile.createNewFile();
			// stdOutFile.createNewFile();
			// stdErrFile.createNewFile();
			// processBuilder.redirectInput(stdInFile);
			processBuilder.redirectOutput(stdOutFile);
			processBuilder.redirectError(stdErrFile);
			// processBuilder.inheritIO();
			process = processBuilder.start();
			exitValue = process.waitFor();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (exitValue != 0) {
			System.exit(exitValue);
		}
	}

	public void init(String[] args) throws ParseException {
		dir = Paths.get(".");

		Options opts = new Options();
		opts.addOption("appId", true,
				"Id of this Container's Application Master.");
		opts.addOption("containerId", true, "Id of this Container.");
		opts.addOption("workflowId", true, "");
		opts.addOption("taskId", true, "");
		opts.addOption("taskName", true, "");
		opts.addOption("langLabel", true, "");
		opts.addOption("signature", true, "");

		CommandLine cliParser = new GnuParser().parse(opts, args);
		appId = cliParser.getOptionValue("appId");
		containerId = cliParser.getOptionValue("containerId");
		workflowId = UUID.fromString(cliParser.getOptionValue("workflowId"));
		taskId = Long.parseLong(cliParser.getOptionValue("taskId"));
		taskName = cliParser.getOptionValue("taskName");
		langLabel = cliParser.getOptionValue("langLabel");
		signature = Long.parseLong(cliParser.getOptionValue("signature"));

		Data.setHdfsDirectoryPrefix(Constant.SANDBOX_DIRECTORY + "/" + appId);
	}

	private Set<Path> parseDir(Path dir) throws IOException {
		Set<Path> files = new HashSet<>();
		for (Path file : Files.newDirectoryStream(dir)) {
			if (Files.isDirectory(file)) {
				files.addAll(parseDir(file));
			} else {
				files.add(file);
			}
		}
		return files;
	}

	public void run() throws IOException {
		Set<Path> oldFiles = parseDir(dir);
		oldFiles.remove(Paths.get("./", Invocation.STDOUT_FILENAME));
		oldFiles.remove(Paths.get("./", Invocation.STDERR_FILENAME));
//		System.out.println("Starting execution");
		exec();
//		System.out.println("Starting traversal");
		Set<Path> newFiles = parseDir(dir);
		newFiles.removeAll(oldFiles);
		newFiles.removeAll(traverseSymbolicLinks(newFiles));
//		System.out.println("Starting stageout");
		stageOut(newFiles);
	}

	private void stageOut(Set<Path> files) {
		Configuration conf = new YarnConfiguration();
		conf.addResource("core-site.xml");
		try {
			FileSystem fs = FileSystem.get(conf);
			for (Path file : files) {
				Data data = new Data(dir.relativize(file).toString());
				data.stageOut(fs, containerId);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private Set<Path> traverseSymbolicLinks(Set<Path> files) throws IOException {
		Set<Path> traversals = new HashSet<>();
		for (Path file : files) {
			if (Files.isSymbolicLink(file)) {
				traversals.add(Paths.get("./", Files.readSymbolicLink(file)
						.toString()));
			}
		}
		return traversals;
	}

}
