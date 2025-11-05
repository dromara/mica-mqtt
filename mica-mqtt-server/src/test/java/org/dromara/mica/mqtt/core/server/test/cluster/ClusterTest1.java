package org.dromara.mica.mqtt.core.server.test.cluster;

import org.tio.server.cluster.core.ClusterApi;
import org.tio.server.cluster.core.ClusterConfig;
import org.tio.server.cluster.core.ClusterImpl;

import java.nio.charset.StandardCharsets;

/**
 * 集群开发测试
 *
 * @author L.cm
 */
public class ClusterTest1 {

	public static void main(String[] args) throws Exception {
		ClusterConfig config = new ClusterConfig("127.0.0.1", 3001, message -> {
			System.out.println(new String(message.getPayload()));
		});

		config.addSeedMember("127.0.0.1", 3001);
		config.addSeedMember("127.0.0.1", 3002);
		config.addSeedMember("127.0.0.1", 3003);

		// TODO L.cm 思考：是不是不应该无限的对离线重试，对方重新连接触发再重新连接

		ClusterApi cluster = new ClusterImpl(config);
		cluster.start();

		cluster.schedule(() -> {
			String message = String.format("hello mica form cluster:%s ns:%s", cluster.getLocalMember(), System.nanoTime());
			cluster.broadcast(message.getBytes(StandardCharsets.UTF_8));
		}, 3000);
	}

}
