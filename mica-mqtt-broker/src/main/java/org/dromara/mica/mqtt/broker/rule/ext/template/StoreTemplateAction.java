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

import net.dreamlu.mica.net.utils.json.JsonUtil;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.ext.aviator.AviatorExprMatcher;
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
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

	private static final Logger logger = LoggerFactory.getLogger(StoreTemplateAction.class);

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
	 * 解析过期策略 {@code maxAge}：支持 ISO-8601 duration（{@code PT7D}）、
	 * {@code d/h/m/s} 后缀以及纯毫秒数。
	 *
	 * @param ref action 配置
	 * @return 保留时长（毫秒），未配置或无法解析时返回 0（不过期）
	 */
	static long retentionMs(ActionRef ref) {
		Object value = ref.getProps().get("maxAge");
		if (value == null) {
			return 0L;
		}
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return 0L;
		}
		try {
			return Duration.parse(text).toMillis();
		} catch (DateTimeParseException ignore) {
			// 非 ISO-8601，继续按后缀解析
		}
		char unit = text.charAt(text.length() - 1);
		if (Character.isDigit(unit)) {
			try {
				return Long.parseLong(text);
			} catch (NumberFormatException e) {
				return 0L;
			}
		}
		long amount;
		try {
			amount = Long.parseLong(text.substring(0, text.length() - 1).trim());
		} catch (NumberFormatException e) {
			return 0L;
		}
		switch (Character.toLowerCase(unit)) {
			case 'd':
				return amount * 24 * 60 * 60 * 1000L;
			case 'h':
				return amount * 60 * 60 * 1000L;
			case 'm':
				return amount * 60 * 1000L;
			case 's':
				return amount * 1000L;
			default:
				return 0L;
		}
	}

	/**
	 * 文件 store：按行追加写入 JSON Lines 文件。
	 * <p>
	 * 配置：{@code props: { storage: file, path: ./data/device-history }}，
	 * 每个 store name 对应 {@code path/{name}.jsonl}；{@code maxRows} 超出后滚动裁剪，
	 * {@code maxAge} 用于过滤过期记录。
	 * </p>
	 */
	public static class FileStoreFunction implements StoreFunction {

		private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();
		private final ConcurrentMap<String, Integer> counters = new ConcurrentHashMap<>();

		@Override
		public void put(ActionRef ref, RuleContext ctx) {
			String path = ref.getString("path");
			if (path == null || path.trim().isEmpty()) {
				throw new IllegalArgumentException("file store requires a non-empty 'path' prop");
			}
			String name = ref.getName();
			Path file = Paths.get(path);
			if (name != null && !name.isEmpty()) {
				file = file.resolve(name + ".jsonl");
			}
			int maxRows = Math.max(1, ref.getInt("maxRows", 10_000));
			String line = toJsonLine(ctx);
			Object lock = locks.computeIfAbsent(file.toString(), k -> new Object());
			synchronized (lock) {
				try {
					Files.createDirectories(file.getParent() == null ? file.toAbsolutePath().getParent() : file.getParent());
					Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					int count = counters.merge(file.toString(), 1, Integer::sum);
					if (count > maxRows * 2) {
						trim(file, maxRows);
					}
				} catch (IOException e) {
					throw new IllegalStateException("Failed to write rule store file: " + file, e);
				}
			}
		}

		/**
		 * 读取最近若干条记录（已按 maxAge 过滤）。
		 *
		 * @param name    store 名
		 * @param path    文件目录
		 * @param limit   最大条数
		 * @param maxAgeMs 保留时长（毫秒），小于等于 0 不过滤
		 * @return 记录列表
		 */
		public List<Record> recent(String name, String path, int limit, long maxAgeMs) {
			Path file = Paths.get(path).resolve(name + ".jsonl");
			if (!Files.exists(file)) {
				return Collections.emptyList();
			}
			List<Record> records = new ArrayList<>();
			long now = System.currentTimeMillis();
			try {
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				int from = Math.max(0, lines.size() - limit);
				for (int i = from; i < lines.size(); i++) {
					Record record = fromJsonLine(lines.get(i));
					if (record == null) {
						continue;
					}
					if (maxAgeMs > 0 && now - record.getTs() > maxAgeMs) {
						continue;
					}
					records.add(record);
				}
			} catch (IOException e) {
				logger.warn("Failed to read rule store file: {}", file, e);
			}
			return records;
		}

		/**
		 * 滚动裁剪：只保留最后 maxRows 行。
		 */
		private void trim(Path file, int maxRows) throws IOException {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			int from = Math.max(0, lines.size() - maxRows);
			StringBuilder sb = new StringBuilder();
			for (int i = from; i < lines.size(); i++) {
				sb.append(lines.get(i)).append(System.lineSeparator());
			}
			Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			counters.put(file.toString(), lines.size() - from);
		}

		private static String toJsonLine(RuleContext ctx) {
			byte[] payload = ctx.getPayload();
			String encoded = payload == null ? "" : Base64.getEncoder().encodeToString(payload);
			return "{\"ts\":" + System.currentTimeMillis()
				+ ",\"clientId\":\"" + escape(ctx.getClientId())
				+ "\",\"topic\":\"" + escape(ctx.getTopic())
				+ "\",\"payload\":\"" + encoded + "\"}";
		}

		private static Record fromJsonLine(String line) {
			if (line == null || line.isEmpty()) {
				return null;
			}
			try {
				Map<?, ?> map = JsonUtil.readValue(line.getBytes(StandardCharsets.UTF_8), Map.class);
				if (map == null) {
					return null;
				}
				Object ts = map.get("ts");
				Object clientId = map.get("clientId");
				Object topic = map.get("topic");
				Object payload = map.get("payload");
				byte[] bytes = payload == null ? new byte[0]
					: Base64.getDecoder().decode(String.valueOf(payload));
				return new Record(ts == null ? 0L : Long.parseLong(String.valueOf(ts)),
					clientId == null ? null : String.valueOf(clientId),
					topic == null ? null : String.valueOf(topic),
					bytes);
			} catch (Exception e) {
				return null;
			}
		}

		private static String escape(String value) {
			if (value == null) {
				return "";
			}
			return value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "\\r");
		}
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
			long maxAgeMs = retentionMs(ref);
			long now = System.currentTimeMillis();
			Deque<Record> deque = data.computeIfAbsent(key, k -> new ArrayDeque<>());
			synchronized (deque) {
				deque.addLast(new Record(now, ctx.getClientId(), ctx.getTopic(), ctx.getPayload()));
				// 过期清理
				if (maxAgeMs > 0) {
					while (!deque.isEmpty() && now - deque.peekFirst().getTs() > maxAgeMs) {
						deque.pollFirst();
					}
				}
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
