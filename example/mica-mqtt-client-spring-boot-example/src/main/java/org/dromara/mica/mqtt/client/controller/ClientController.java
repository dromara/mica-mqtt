package org.dromara.mica.mqtt.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dromara.mica.mqtt.client.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Mqtt::客户端")
@RequestMapping("/mqtt/client")
@RestController
public class ClientController {

	@Autowired
	private ClientService service;

	@Operation(summary = "publish")
	@PostMapping("/publish")
	public boolean publish(@RequestBody String body) {
		service.publish(body);
		service.publishHelloInterfaceA(body);
		service.publishHelloInterfaceB(body);
		return true;
	}

	@Operation(summary = "sub")
	@GetMapping("/sub")
	public boolean sub() {
		return service.sub();
	}

}
