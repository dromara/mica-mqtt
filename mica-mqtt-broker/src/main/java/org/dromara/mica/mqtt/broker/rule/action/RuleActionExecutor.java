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

package org.dromara.mica.mqtt.broker.rule.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规则引擎的 action 执行线程池。
 * <p>
 * 规则命中后整条 action 链作为一个任务提交到这里，池内串行执行。这样既保证
 * 「actions 按声明顺序执行」与 {@code stopOnError} 语义，又让 t-io 的 IO 线程
 * 不被慢后端（HTTP / MQTT）阻塞。
 * </p>
 * <p>
 * 拒绝策略为 {@link ThreadPoolExecutor.CallerRunsPolicy}：队列满时由提交方
 * （IO 线程）自己执行，起到天然背压作用，且不会出现任务被丢弃。
 * </p>
 *
 * @author L.cm
 */
public class RuleActionExecutor implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(RuleActionExecutor.class);

	/**
	 * 线程池名称。
	 */
	public static final String POOL_NAME = "rule-action-pool";

	/**
	 * 默认核心线程数：2 * CPU。
	 */
	public static final int DEFAULT_CORE_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);

	/**
	 * 默认最大线程数：32 * CPU。
	 */
	public static final int DEFAULT_MAX_SIZE = Math.max(DEFAULT_CORE_SIZE, Runtime.getRuntime().availableProcessors() * 32);

	/**
	 * 默认队列容量。
	 */
	public static final int DEFAULT_QUEUE_SIZE = 1024;

	private final ExecutorService executor;

	/**
	 * 超时看门狗：单线程，负责在规则执行超时后中断规则任务。
	 */
	private final ScheduledExecutorService watchdog;

	/**
	 * 用默认参数创建线程池。
	 */
	public RuleActionExecutor() {
		this(DEFAULT_CORE_SIZE, DEFAULT_MAX_SIZE, DEFAULT_QUEUE_SIZE);
	}

	/**
	 * 创建线程池。
	 *
	 * @param coreSize    核心线程数，小于 1 时取默认值
	 * @param maxSize     最大线程数，小于 coreSize 时取 coreSize
	 * @param queueSize   队列容量，小于 1 时取默认值
	 */
	public RuleActionExecutor(int coreSize, int maxSize, int queueSize) {
		int core = coreSize > 0 ? coreSize : DEFAULT_CORE_SIZE;
		int max = Math.max(core, maxSize);
		int queue = queueSize > 0 ? queueSize : DEFAULT_QUEUE_SIZE;
		this.executor = new ThreadPoolExecutor(
			core, max, 60L, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(queue),
			new NamedThreadFactory(),
			new ThreadPoolExecutor.CallerRunsPolicy());
		this.watchdog = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(POOL_NAME + "-watchdog"));
		logger.info("Rule action pool started: core={}, max={}, queue={}", core, max, queue);
	}

	/**
	 * 提交一个 action 任务。
	 *
	 * @param task 任务
	 */
	public void execute(Runnable task) {
		executor.execute(task);
	}

	/**
	 * 提交一个 action 任务并返回其 Future。
	 *
	 * @param task 任务
	 * @return 任务对应的 Future
	 */
	public java.util.concurrent.Future<?> submit(Runnable task) {
		return executor.submit(task);
	}

	/**
	 * 延时执行一次任务（用于超时看门狗）。
	 *
	 * @param task    任务
	 * @param delayMs 延时（毫秒）
	 * @return 调度句柄
	 */
	public ScheduledFuture<?> schedule(Runnable task, long delayMs) {
		return watchdog.schedule(task, delayMs, TimeUnit.MILLISECONDS);
	}

	/**
	 * @return 线程池是否已关闭
	 */
	public boolean isShutdown() {
		return executor.isShutdown();
	}

	/**
	 * 关闭线程池：先拒绝新任务，再等待正在执行的任务结束。
	 */
	@Override
	public void close() {
		watchdog.shutdownNow();
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 守护线程工厂，线程名带序号便于排查。
	 */
	private static final class NamedThreadFactory implements ThreadFactory {
		private final String prefix;
		private final AtomicInteger idx = new AtomicInteger();

		NamedThreadFactory() {
			this(POOL_NAME);
		}

		NamedThreadFactory(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, prefix + "-" + idx.incrementAndGet());
			t.setDaemon(true);
			return t;
		}
	}
}
