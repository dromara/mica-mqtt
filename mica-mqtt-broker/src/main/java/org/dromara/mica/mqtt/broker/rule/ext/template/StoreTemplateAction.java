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

package org.dromara.mica.mqtt.broker.rule.ext.template;

import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.ext.aviator.AviatorExprMatcher;
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * store 模板：把消息持久化。
 *
 * <p>YAML 配置：
 * <pre>
 * - type: store
 *   name: device-history
 *   props:
 *     storage: memory
 *     filter: "payload.temperature > 80"
 *     maxRows: 10000
 * </pre>
 *
 * @author L.cm
 */
public class StoreTemplateAction implements Action {

	private static final Map<String, StoreFunction> STORAGE_REGISTRY = new ConcurrentHashMap<>();
	private static final AtomicReference<StoreFunction> FALLBACK = new AtomicReference<>();

	private final ActionRef ref;
	private AviatorExprMatcher filter;
	private StoreFunction fn;

	public StoreTemplateAction(ActionRef ref) {
		this.ref = ref;
	}

	public static void registerStorage(String type, StoreFunction fn) {
		STORAGE_REGISTRY.put(type, fn);
	}

	public static void setFallback(StoreFunction fn) {
		FALLBACK.set(fn);
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	private void ensure() {
		if (fn != null) {
			return;
		}
		String type = ref.getString("storage", "memory");
		fn = STORAGE_REGISTRY.get(type);
		if (fn == null) {
			fn = FALLBACK.get();
		}
		if (fn == null) {
			throw new IllegalStateException("No store registered for type: " + type);
		}
		String filterExpr = ref.getString("filter");
		if (filterExpr != null && !filterExpr.isEmpty()) {
			filter = new AviatorExprMatcher(filterExpr);
		}
	}

	@Override
	public void send(RuleContext ctx) throws Exception {
		ensure();
		if (filter != null) {
			Object pass = filter.execute(AviatorExprMatcher.envOf(ctx));
			if (!AviatorExprMatcher.asBool(pass)) {
				return;
			}
		}
		fn.put(ref, ctx);
	}

	@FunctionalInterface
	public interface StoreFunction {
		void put(ActionRef ref, RuleContext ctx);
	}

	/**
	 * 默认内存 store。
	 */
	public static class MemoryStoreFunction implements StoreFunction {

		private final ConcurrentMap<String, Deque<Record>> data = new ConcurrentHashMap<>();

		@Override
		public void put(ActionRef ref, RuleContext ctx) {
			String key = ref.getName();
			int maxRows = ref.getInt("maxRows", 10_000);
			Deque<Record> deque = data.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
			synchronized (deque) {
				deque.addLast(new Record(System.currentTimeMillis(), ctx.getClientId(),
					ctx.getTopic(), ctx.getPayload()));
				while (deque.size() > maxRows) {
					deque.pollFirst();
				}
			}
		}

		public java.util.List<Record> recent(String name, int limit) {
			Deque<Record> deque = data.get(name);
			if (deque == null) {
				return java.util.Collections.emptyList();
			}
			java.util.List<Record> snapshot = new java.util.ArrayList<>(deque);
			int from = Math.max(0, snapshot.size() - limit);
			return snapshot.subList(from, snapshot.size());
		}
	}

	public static class Record {
		private final long ts;
		private final String clientId;
		private final String topic;
		private final byte[] payload;

		public Record(long ts, String clientId, String topic, byte[] payload) {
			this.ts = ts;
			this.clientId = clientId;
			this.topic = topic;
			this.payload = payload;
		}

		public long getTs() { return ts; }
		public String getClientId() { return clientId; }
		public String getTopic() { return topic; }
		public byte[] getPayload() { return payload; }
	}

	/**
	 * ActionFactory：注册 type=store。
	 */
	public static class Factory implements ActionFactory {
		@Override
		public String getType() {
			return "store";
		}

		@Override
		public Action create(ActionRef ref) {
			return new StoreTemplateAction(ref);
		}
	}
}
