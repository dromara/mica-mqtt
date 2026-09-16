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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
 * <p>{@link StoreFunction} 由装配流程通过 {@link TemplateServices} 注入。
 *
 * @author L.cm
 */
public class StoreTemplateAction implements Action {

	private final ActionRef ref;
	private final StoreFunction fn;
	/**
	 * 可选的过滤表达式，构造期一次性编译完成（两个字段均 final，避免惰性双检导致
	 * {@code fn != null} 而 {@code filter} 仍为 null 的可见性问题）。
	 */
	private final AviatorExprMatcher filter;

	public StoreTemplateAction(ActionRef ref, StoreFunction fn) {
		this.ref = ref;
		this.fn = fn;
		this.filter = compileFilter(ref);
	}

	private static AviatorExprMatcher compileFilter(ActionRef ref) {
		String filterExpr = ref.getString("filter");
		if (filterExpr == null || filterExpr.isEmpty()) {
			return null;
		}
		return new AviatorExprMatcher(filterExpr);
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	@Override
	public void send(RuleContext ctx) {
		if (fn == null) {
			throw new IllegalStateException("store action is not wired with a StoreFunction");
		}
		if (filter != null && !AviatorExprMatcher.asBool(filter.execute(AviatorExprMatcher.envOf(ctx)))) {
			return;
		}
		fn.put(ref, ctx);
	}

	@FunctionalInterface
	public interface StoreFunction {
		void put(ActionRef ref, RuleContext ctx);
	}

	/**
	 * 默认内存 store：每个 name 一条环形缓冲（保留最新 maxRows 条）。
	 */
	public static class MemoryStoreFunction implements StoreFunction {

		private final ConcurrentMap<String, Deque<Record>> data = new ConcurrentHashMap<>();

		@Override
		public void put(ActionRef ref, RuleContext ctx) {
			String key = ref.getName();
			int maxRows = Math.max(1, ref.getInt("maxRows", 10_000));
			Deque<Record> deque = data.computeIfAbsent(key, k -> new ArrayDeque<>());
			synchronized (deque) {
				deque.addLast(new Record(System.currentTimeMillis(), ctx.getClientId(),
					ctx.getTopic(), ctx.getPayload()));
				while (deque.size() > maxRows) {
					deque.pollFirst();
				}
			}
		}

		public List<Record> recent(String name, int limit) {
			Deque<Record> deque = data.get(name);
			if (deque == null) {
				return Collections.emptyList();
			}
			List<Record> snapshot;
			synchronized (deque) {
				snapshot = new ArrayList<>(deque);
			}
			int from = Math.max(0, snapshot.size() - limit);
			return snapshot.subList(from, snapshot.size());
		}

		public int size(String name) {
			Deque<Record> deque = data.get(name);
			if (deque == null) {
				return 0;
			}
			synchronized (deque) {
				return deque.size();
			}
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

		public long getTs() {
			return ts;
		}

		public String getClientId() {
			return clientId;
		}

		public String getTopic() {
			return topic;
		}

		public byte[] getPayload() {
			return payload;
		}
	}

	/**
	 * ActionFactory：注册 type=store。
	 */
	public static class Factory implements TemplateActionFactory {
		private volatile TemplateServices services;

		@Override
		public String getType() {
			return "store";
		}

		@Override
		public void setTemplateServices(TemplateServices services) {
			this.services = services;
		}

		@Override
		public Action create(ActionRef ref) {
			TemplateServices current = services;
			String type = ref.getString("storage", TemplateServices.MEMORY_STORE);
			StoreFunction fn = current == null ? null : current.getStore(type);
			return new StoreTemplateAction(ref, fn);
		}
	}
}
