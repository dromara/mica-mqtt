package org.dromara.mica.mqtt.core.server.session;

/**
 * 订阅数据内部类 - 使用享元模式复用实例
 * MQTT QoS 只有 0/1/2 三个值，noLocal 只有 true/false 两个值
 * 总共 6 种组合，预先创建实例池，避免频繁创建对象
 *
 * @author L.cm
 */
final class SubscribeData {
	/**
	 * 实例池：[qos][noLocal ? 1 : 0]
	 * 索引：qos=0/1/2, noLocal=0(false)/1(true)
	 */
	private static final SubscribeData[][] POOL = new SubscribeData[3][2];
	/**
	 * 解码缓存：直接通过 encoded 值索引获取实例，避免位运算
	 * encoded 有效值：0,1,2,4,5,6 对应 6 种组合
	 */
	private static final SubscribeData[] DECODE_CACHE = new SubscribeData[8];

	static {
		for (int qos = 0; qos <= 2; qos++) {
			for (int noLocal = 0; noLocal <= 1; noLocal++) {
				SubscribeData data = new SubscribeData((byte) qos, noLocal == 1);
				POOL[qos][noLocal] = data;
				// 同时填充解码缓存，通过 encoded 值直接索引
				DECODE_CACHE[data.encoded] = data;
			}
		}
	}

	final byte qos;
	final boolean noLocal;
	final byte encoded;

	private SubscribeData(byte qos, boolean noLocal) {
		this.qos = qos;
		this.noLocal = noLocal;
		this.encoded = (byte) ((qos & 0x03) | ((noLocal ? 1 : 0) << 2));
	}

	/**
	 * 获取 SubscribeData 实例（享元模式）
	 *
	 * @param qos     QoS 级别 (0-2)
	 * @param noLocal No Local 标志
	 * @return SubscribeData 实例
	 */
	static SubscribeData of(byte qos, boolean noLocal) {
		// 合法的 QoS 值从池中获取
		if (qos >= 0 && qos <= 2) {
			return POOL[qos][noLocal ? 1 : 0];
		}
		// QoS 非法值（理论上不会出现）降级为创建新实例
		return new SubscribeData(qos, noLocal);
	}

	/**
	 * 从字节编码中解析（直接通过缓存数组获取，O(1) 性能）
	 * 编码格式: bit 0-1: qos, bit 2: noLocal
	 *
	 * @param encoded 编码的字节值
	 * @return SubscribeData 实例
	 */
	static SubscribeData decode(byte encoded) {
		// 直接通过 encoded 值索引缓存数组，避免位运算
		// encoded 有效值范围: 0-7，对应索引直接可用
		SubscribeData data = DECODE_CACHE[encoded & 0x07];
		if (data != null) {
			return data;
		}
		// 降级处理：理论上不会到这里，除非 encoded 值非法
		byte qos = (byte) (encoded & 0x03);
		boolean noLocal = (encoded & 0x04) != 0;
		return of(qos, noLocal);
	}
}
