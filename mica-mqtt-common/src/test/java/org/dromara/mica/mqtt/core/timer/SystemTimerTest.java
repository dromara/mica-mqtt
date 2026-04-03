package org.dromara.mica.mqtt.core.timer;

import net.dreamlu.mica.net.utils.timer.SystemTimer;
import net.dreamlu.mica.net.utils.timer.TimingWheelThread;
import org.dromara.mica.mqtt.core.util.timer.AckTimerTask;

import java.util.concurrent.TimeUnit;

public class SystemTimerTest {

	public static void main(String[] args) throws InterruptedException {
		SystemTimer systemTimer = new SystemTimer("timer");

		TimingWheelThread timingWheelThread = new TimingWheelThread(systemTimer);
		timingWheelThread.start();

		System.out.println(System.currentTimeMillis());
		systemTimer.add(new AckTimerTask(systemTimer, () -> {
			System.out.println("hello!");
		}, 5, 5));
		System.out.println(System.nanoTime());

		TimeUnit.MINUTES.sleep(10L);
	}
}
