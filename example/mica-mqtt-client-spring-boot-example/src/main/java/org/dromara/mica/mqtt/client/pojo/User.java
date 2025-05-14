package org.dromara.mica.mqtt.client.pojo;

import lombok.Data;

@Data
public class User<T> {
	private String name;
	private T girlfriend;
}
