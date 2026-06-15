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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round-robin subscriber selection within each shared-subscription group.
 * <p>
 * A per-group atomic counter is incremented on every pick and used to index
 * into the candidate list modulo its current size.  This guarantees fair
 * distribution of messages across all subscribers in a group <em>as seen from
 * this particular publisher node</em>.
 * </p>
 * <p>
 * <strong>Note</strong>: The counter is local to this node.  In a multi-node
 * cluster each publisher node maintains its own counter, so global round-robin
 * across the whole cluster is not guaranteed.  Within a single publisher node
 * the distribution is perfectly even.
 * </p>
 *
 * @author L.cm
 * @see SharedSubscriptionStrategy
 * @since 1.0.0
 */
public class RoundRobinStrategy implements SharedSubscriptionStrategy {

	/**
	 * Per-group monotonically increasing counter.
	 * Using a {@link ConcurrentHashMap} so that different groups are independent
	 * and do not contend on the same lock.
	 */
	private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

	@Override
	public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		long seq = counters.computeIfAbsent(groupName, k -> new AtomicLong(0L)).getAndIncrement();
		return candidates.get((int) (seq % candidates.size()));
	}

	@Override
	public String name() {
		return "round_robin";
	}
}
