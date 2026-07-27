package org.dromara.mica.mqtt.benchmark;

import net.dreamlu.mica.net.utils.hutool.StrUtil;
import net.dreamlu.mica.net.utils.thread.ThreadUtils;
import net.dreamlu.mica.net.utils.thread.pool.SynThreadPoolExecutor;
import net.dreamlu.mica.net.utils.timer.DefaultTimerTaskService;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * mqtt 发布端测试
 *
 * @author L.cm
 */
public class MqttPublishBench {

	public static void main(String[] args) {
		int clientCount = 10;
		int publishCount = 10000;
		MqttQoS qos = MqttQoS.QOS0;
		List<MqttClient> clients = getClient(clientCount);

		// 统计计数器
		LongAdder totalCount = new LongAdder();
		LongAdder totalBytes = new LongAdder();
		long startTime = System.currentTimeMillis();

		// 每秒打印统计信息
		NumberFormat nf = NumberFormat.getInstance();
		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
			long elapsed = System.currentTimeMillis() - startTime;
			long count = totalCount.sum();
			long bytes = totalBytes.sum();
			double seconds = elapsed / 1000.0;
			long msgPerSec = (long) (count / seconds);
			double mbPerSec = bytes / seconds / 1024.0 / 1024.0;
			System.out.printf("=== [%ds] Published: %s msgs, Throughput: %s msg/s, %.2f MB/s ===%n",
				elapsed / 1000, nf.format(count), nf.format(msgPerSec), mbPerSec);
		}, 1L, 1L, TimeUnit.SECONDS);

		Executors.newScheduledThreadPool(ThreadUtils.AVAILABLE_PROCESSORS).scheduleWithFixedDelay(() -> {
			for (MqttClient mqttClient : clients) {
				for (int j = 0; j < publishCount; j++) {
					byte[] payload = new byte[1024 + j];
					Arrays.fill(payload, (byte) -1);
					mqttClient.publish("/topic/" + j, payload, qos);
					totalCount.increment();
					totalBytes.add(payload.length);
				}
			}
		}, 1L, 1L, TimeUnit.SECONDS);
	}

	public static List<MqttClient> getClient(int clientCount) {
		SynThreadPoolExecutor tioExecutor = ThreadUtils.getTioExecutor();
		ExecutorService groupExecutor = ThreadUtils.getGroupExecutor();
		TimerTaskService taskService = new DefaultTimerTaskService();
		List<MqttClient> clients = new ArrayList<>();
		for (int i = 0; i < clientCount; i++) {
			MqttClient client = MqttClient.create()
				.clientId(StrUtil.getNanoId())
				.tioExecutor(tioExecutor)
				.groupExecutor(groupExecutor)
				.mqttExecutor(groupExecutor)
				.taskService(taskService)
				.connectSync();
			clients.add(client);
		}
		return clients;
	}

}
