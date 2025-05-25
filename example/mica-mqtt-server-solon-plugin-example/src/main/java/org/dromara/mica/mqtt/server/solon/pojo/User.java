package org.dromara.mica.mqtt.server.solon.pojo;

import lombok.Data;

@Data
public class User<T> {
	private String name;
	private T girlfriend;

	public static User newUser(){
		User<User> user1 = new User();
		user1.setName("name1");

		User<User> user2 = new User();
		user2.setName("name2");
		user2.setGirlfriend(user1);
		return user2;
	}
}
