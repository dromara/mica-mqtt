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

/**
 * Strategy interface for selecting a single subscriber from a shared-subscription group.
 * <p>
 * In the V2 dispatcher model every broker node holds a full replica of the cluster
 * subscription table (inherited from V1).  When a message is published to a topic
 * that has {@code $share/<group>/<topic>} or {@code $queue/<topic>} subscribers, the
 * publisher's node calls this strategy to pick <em>exactly one</em> subscriber from
 * the group candidate list.  The message is then forwarded to that subscriber's node
 * only, eliminating the duplicate delivery that occurred with the V1 full-broadcast
 * approach.
 * </p>
 * <p>
 * Implementations <strong>must</strong> be thread-safe — a single strategy instance
 * is shared across all message-handler threads on a node.
 * </p>
 *
 * @author L.cm
 * @see RandomStrategy
 * @see RoundRobinStrategy
 * @see LocalFirstStrategy
 * @see HashClientStrategy
 * @see StickyStrategy
 * @since 2.6.0
 */
public interface SharedSubscriptionStrategy {

	/**
	 * Selects one subscriber from the candidate list for the given group.
	 *
	 * @param groupName     the shared-subscription group name extracted from the topic
	 *                      filter (e.g. {@code "g1"} for {@code $share/g1/sensor/temp});
	 *                      for {@code $queue/} subscriptions a fixed placeholder is used
	 * @param candidates    all active subscribers for this group across the entire cluster
	 *                      (local + remote); never null, but may be empty
	 * @param localNodeId   the node ID of the node executing this pick, used by
	 *                      locality-aware strategies such as {@link LocalFirstStrategy}
	 * @param message       the MQTT message being dispatched; used by hash-based
	 *                      strategies that derive the target from message attributes
	 * @return the selected {@link Subscribe}, or {@code null} if no active subscriber
	 *         is available in the group
	 */
	Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message);

	/**
	 * Returns the unique name of this strategy used for configuration and monitoring.
	 *
	 * @return the strategy name (e.g. {@code "random"}, {@code "round_robin"})
	 */
	String name();
}
