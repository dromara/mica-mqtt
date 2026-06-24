/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.broker.cluster.pipeline.strategy;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Local-first subscriber selection — the <strong>recommended default</strong> strategy.
 * <p>
 * This strategy prefers subscribers that are connected to the <em>same node</em> as
 * the publisher.  By avoiding cross-node forwarding whenever possible, it minimises
 * inter-broker network traffic and reduces end-to-end delivery latency.
 * </p>
 * <p>
 * <strong>Selection algorithm</strong>:
 * <ol>
 *   <li>Collect all candidates whose {@code clientId} maps to the local node.</li>
 *   <li>If any local candidates exist, return one chosen at random among them.</li>
 *   <li>Otherwise fall back to a random pick among all remote candidates.</li>
 * </ol>
 * </p>
 * <p>
 * This strategy works best when publishers and subscribers are evenly distributed
 * across nodes, which is the typical IoT deployment pattern.
 * </p>
 *
 * @author L.cm
 * @see SharedSubscriptionStrategy
 * @since 2.6.0
 */
public class LocalFirstStrategy implements SharedSubscriptionStrategy {

	/** Node-ID to client-ID lookup supplied by the cluster session manager. */
	private final Function<String, String> clientNodeResolver;

	/**
	 * Constructs a {@code LocalFirstStrategy} with the supplied node resolver.
	 *
	 * @param clientNodeResolver a function that maps a {@code clientId} to its current
	 *                           node ID; returns {@code null} if the client is local
	 *                           or its node is unknown
	 */
	public LocalFirstStrategy(Function<String, String> clientNodeResolver) {
		this.clientNodeResolver = clientNodeResolver;
	}

	@Override
	public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}

		// Collect candidates that are local to this node.
		List<Subscribe> locals = new ArrayList<>();
		for (Subscribe s : candidates) {
			String nodeId = clientNodeResolver.apply(s.getClientId());
			if (nodeId == null || nodeId.equals(localNodeId)) {
				locals.add(s);
			}
		}

		if (!locals.isEmpty()) {
			return locals.get(ThreadLocalRandom.current().nextInt(locals.size()));
		}
		// No local subscribers — fall back to a random remote pick.
		return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
	}

	@Override
	public String name() {
		return "local_first";
	}
}
