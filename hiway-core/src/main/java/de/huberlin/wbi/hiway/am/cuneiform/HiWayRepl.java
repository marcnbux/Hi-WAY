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
package de.huberlin.wbi.hiway.am.cuneiform;

import java.util.UUID;

import de.huberlin.wbi.cuneiform.core.repl.BaseRepl;
import de.huberlin.wbi.cuneiform.core.semanticmodel.CompoundExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NotDerivableException;
import de.huberlin.wbi.cuneiform.core.ticketsrc.TicketSrcActor;

//Repl - Read evaluation print loop
public class HiWayRepl extends BaseRepl {

	private CuneiformApplicationMaster am;

	public HiWayRepl(TicketSrcActor ticketSrc, CuneiformApplicationMaster am) {
		super(ticketSrc, null);
		this.am = am;
	}

	@Override
	public void queryFailedPost(UUID queryId, Long ticketId, Exception e, String script, String stdOut, String stdErr) {
		CuneiformApplicationMaster.getLog().info("Query failed.");
		am.setDone();
	}

	@Override
	public void queryFinishedPost(UUID queryId, CompoundExpr result) {
		CuneiformApplicationMaster.getLog().info("Query finished.");
		am.setDone();
		try {
			for (String output : result.normalize()) {
				if (am.getFiles().containsKey(output)) {
					am.getFiles().get(output).setOutput(true);
				}
			}
		} catch (NotDerivableException e) {
			CuneiformApplicationMaster.onError(e);
		}
	}

	@Override
	public void queryStartedPost(UUID runId) {
	}

}