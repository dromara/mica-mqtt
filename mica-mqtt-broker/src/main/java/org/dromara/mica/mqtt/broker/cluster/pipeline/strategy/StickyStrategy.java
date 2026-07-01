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

/**
 * Sticky subscriber selection — messages in a group always go to the same subscriber.
 * <p>
 * The first time a message arrives for a group, one subscriber is selected
 * (the first in the candidate list, providing stability).  Subsequent messages
 * for the same group continue to use that subscriber as long as it is still
 * present in the candidate list.  If the sticky subscriber leaves (disconnect /
 * unsubscribe), the next call re-selects from the remaining candidates and
 * persists that new choice.
 * </p>
 * <p>
 * Use cases:
 * </p>
 * <ul>
 *   <li>Applications that maintain in-process state (e.g. aggregations) and
 *       need all messages for a device to land on the same subscriber instance</li>
 *   <li>Long-lived connection affinity where reconnection cost is high</li>
 * </ul>
 * <p>
 * <strong>Warning</strong>: load distribution is <em>not</em> guaranteed.  If one
 * subscriber handles a hot topic group it may become overloaded.
 * </p>
 *
 * @author L.cm
 * @see SharedSubscriptionStrategy
 * @since 2.6.0
 */
public class StickyStrategy implements SharedSubscriptionStrategy {

	/**
	 * Maps group name → the clientId that was last selected for that group.
	 */
	private final ConcurrentMap<String, String> stickyMap = new ConcurrentHashMap<>();

	@Override
	public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}

		String lastClientId = stickyMap.get(groupName);
		if (lastClientId != null) {
			// Try to reuse the previously selected subscriber.
			for (Subscribe s : candidates) {
				if (s.getClientId().equals(lastClientId)) {
					return s;
				}
			}
		}

		// Sticky subscriber is gone (disconnected/unsubscribed) — re-select.
		Subscribe picked = candidates.get(0);
		stickyMap.put(groupName, picked.getClientId());
		return picked;
	}

	@Override
	public String name() {
		return "sticky";
	}
}
