package org.dromara.mica.mqtt.server.solon.test.task;

import org.noear.solon.Solon;
import org.noear.solon.scheduling.annotation.EnableScheduling;

/**
 * <b>(ServerTest)</b>
 *
 * @author Peigen
 * @version 1.0.0
 * @since 2023/7/15
 */
@EnableScheduling
public class ServerTest {

	public static void main(String[] args) {
		Solon.start(ServerTest.class,args);
	}

}
