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

package org.dromara.mica.mqtt.core.util.timer;

import net.dreamlu.mica.net.utils.timer.Timer;
import net.dreamlu.mica.net.utils.timer.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * ack TimerTask
 *
 * @author L.cm
 */
public class AckTimerTask extends TimerTask {
	private static final Logger log = LoggerFactory.getLogger(AckTimerTask.class);

	/**
	 * task
	 */
	private final Timer timer;
	/**
	 * 需要执行的函数
	 */
	private final Runnable command;
	/**
	 * qos 1~2 重试次数
	 */
	private final int maxRetryCount;
	/**
	 * 当前自行的次数，默认从第二次开始，因为进重试前已经执行过一次。
	 */
	private int count = 1;

	public AckTimerTask(Timer timer, Runnable command, int maxRetryCount, int retryIntervalSecs) {
		super(TimeUnit.SECONDS.toMillis(retryIntervalSecs));
		this.timer = Objects.requireNonNull(timer, "Timer is null.");
		this.command = Objects.requireNonNull(command, "Runnable command is null.");
		this.maxRetryCount = maxRetryCount;
	}

	@Override
	public void run() {
		if (++count <= maxRetryCount + 1) {
			// 收先添加任务，保证后续执行
			timer.add(this);
			log.debug("Mqtt ack task retry running count：{}.", count);
			try {
				command.run();
			} catch (Exception e) {
				log.error("Mqtt ack task error ", e);
			}
		}
	}

}
