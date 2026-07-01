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
 * Hash-based subscriber selection that routes messages consistently to the same subscriber.
 * <p>
 * The index into the candidate list is derived by hashing a combination of the
 * group name and the MQTT message ID ({@link Message#getId()}).  When the message ID
 * is {@code null} the topic string is used as a fallback hash source.
 * </p>
 * <p>
 * This strategy is suitable when:
 * </p>
 * <ul>
 *   <li>Messages within the same group should be processed by the same subscriber
 *       (ordering guarantee per message sequence)</li>
 *   <li>The application relies on state locality (e.g. caches warmed per subscriber)</li>
 * </ul>
 * <p>
 * <strong>Limitation</strong>: the distribution is deterministic but not necessarily
 * uniform; neighbouring hash values may map to the same subscriber bucket.
 * </p>
 *
 * @author L.cm
 * @see SharedSubscriptionStrategy
 * @since 2.6.0
 */
public class HashClientStrategy implements SharedSubscriptionStrategy {

	@Override
	public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		int hash;
		if (message != null && message.getId() != null) {
			hash = message.getId() ^ groupName.hashCode();
		} else if (message != null && message.getTopic() != null) {
			hash = message.getTopic().hashCode() ^ groupName.hashCode();
		} else {
			hash = groupName.hashCode();
		}
		return candidates.get((hash & Integer.MAX_VALUE) % candidates.size());
	}

	@Override
	public String name() {
		return "hash_client";
	}
}
