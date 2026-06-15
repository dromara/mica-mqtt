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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Randomly selects one subscriber from the group candidate list.
 * <p>
 * This is the simplest possible strategy: it picks a subscriber uniformly at
 * random on every invocation, maintaining no state.  It works well when:
 * <ul>
 *   <li>Subscribers are roughly equal in processing capacity</li>
 *   <li>Message volume is high enough that the law of large numbers distributes
 *       load evenly over time</li>
 *   <li>Predictability or ordering is <em>not</em> required</li>
 * </ul>
 * Downside: individual hot-spots are possible in the short term because two
 * consecutive messages may land on the same subscriber by chance.
 * </p>
 *
 * @author L.cm
 * @see SharedSubscriptionStrategy
 * @since 2.6.0
 */
public class RandomStrategy implements SharedSubscriptionStrategy {

	@Override
	public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
	}

	@Override
	public String name() {
		return "random";
	}
}
