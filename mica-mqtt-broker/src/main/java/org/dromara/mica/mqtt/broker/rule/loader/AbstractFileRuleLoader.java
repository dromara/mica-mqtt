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

package org.dromara.mica.mqtt.broker.rule.loader;

import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.RuleChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文件型规则加载器基类：启动期加载 + 按文件修改时间热加载。
 * <p>
 * 热加载采用 mtime 轮询（默认 5 秒），文件变化后重新解析，
 * 变更的规则触发 {@code onSaved}，消失的规则触发 {@code onDeleted}。
 * </p>
 *
 * @author L.cm
 */
public abstract class AbstractFileRuleLoader implements RuleLoader {

	private static final Logger logger = LoggerFactory.getLogger(AbstractFileRuleLoader.class);

	/**
	 * 默认轮询间隔：5 秒。
	 */
	public static final long DEFAULT_WATCH_INTERVAL_MS = 5000L;

	protected final Path path;
	private final long watchIntervalMs;
	private final Map<String, Rule> lastLoaded = new ConcurrentHashMap<>();
	private volatile long lastModifiedTime;
	private volatile ScheduledExecutorService watcher;

	protected AbstractFileRuleLoader(Path path) {
		this(path, DEFAULT_WATCH_INTERVAL_MS);
	}

	protected AbstractFileRuleLoader(Path path, long watchIntervalMs) {
		this.path = path;
		this.watchIntervalMs = watchIntervalMs;
	}
	/**
	 * 读取并解析配置文件内容。
	 *
	 * @return 解析后的配置树（Map 或 List）
	 * @throws IOException 读取失败
	 */
	protected abstract Object readConfig() throws IOException;

	/**
	 * @return 配置文件路径
	 */
	public Path getPath() {
		return path;
	}

	@Override
	public String getSource() {
		return path.toString();
	}

	@Override
	public List<Rule> loadAll() {
		Object config;
		try {
			config = readConfig();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read rule file: " + path, e);
		}
		List<Rule> rules = RuleConfigParser.parse(config);
		lastLoaded.clear();
		for (Rule rule : rules) {
			lastLoaded.put(rule.getId(), rule);
		}
		lastModifiedTime = lastModifiedTime();
		return rules;
	}

	@Override
	public void setListener(RuleChangeListener listener) {
		if (listener == null || watchIntervalMs <= 0) {
			return;
		}
		startWatcher(listener);
	}

	/**
	 * 停止热加载轮询。
	 */
	@Override
	public void stop() {
		ScheduledExecutorService svc = this.watcher;
		if (svc != null) {
			svc.shutdownNow();
			this.watcher = null;
		}
	}

	private void startWatcher(RuleChangeListener listener) {
		ScheduledExecutorService svc = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "rule-loader-watch-" + path.getFileName());
			t.setDaemon(true);
			return t;
		});
		svc.scheduleWithFixedDelay(() -> {
			try {
				reload(listener);
			} catch (Exception e) {
				logger.warn("Failed to reload rules from {}", path, e);
			}
		}, watchIntervalMs, watchIntervalMs, TimeUnit.MILLISECONDS);
		this.watcher = svc;
	}

	/**
	 * 文件变化后重新加载并派发变更事件。
	 */
	private void reload(RuleChangeListener listener) throws IOException {
		long modified = lastModifiedTime();
		if (modified == 0 || modified == lastModifiedTime) {
			return;
		}
		lastModifiedTime = modified;
		List<Rule> rules = RuleConfigParser.parse(readConfig());
		Map<String, Rule> current = new HashMap<>();
		for (Rule rule : rules) {
			current.put(rule.getId(), rule);
		}
		for (Rule rule : rules) {
			listener.onSaved(rule);
		}
		for (String id : lastLoaded.keySet()) {
			if (!current.containsKey(id)) {
				listener.onDeleted(id);
			}
		}
		lastLoaded.clear();
		lastLoaded.putAll(current);
		logger.info("Rules reloaded from {}: {} rules", path, current.size());
	}

	private long lastModifiedTime() {
		try {
			return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
		} catch (IOException e) {
			return 0L;
		}
	}
}
