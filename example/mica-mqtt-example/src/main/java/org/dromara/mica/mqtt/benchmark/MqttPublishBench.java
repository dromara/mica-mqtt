package org.dromara.mica.mqtt.benchmark;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.thread.ThreadUtils;
import org.tio.utils.thread.pool.SynThreadPoolExecutor;
import org.tio.utils.timer.DefaultTimerTaskService;
import org.tio.utils.timer.TimerTaskService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
		Executors.newScheduledThreadPool(ThreadUtils.AVAILABLE_PROCESSORS).scheduleWithFixedDelay(() -> {
			for (MqttClient mqttClient : clients) {
				for (int j = 0; j < publishCount; j++) {
					byte[] payload = new byte[1024 + j];
					Arrays.fill(payload, (byte) -1);
					mqttClient.publish("/topic/" + j, payload, qos);
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
