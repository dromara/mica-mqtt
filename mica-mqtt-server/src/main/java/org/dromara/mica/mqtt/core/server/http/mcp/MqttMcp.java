package org.dromara.mica.mqtt.core.server.http.mcp;


import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.http.api.form.PublishForm;
import org.dromara.mica.mqtt.core.server.http.handler.MqttHttpRoutes;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.tio.core.stat.vo.StatVo;
import org.tio.http.common.Method;
import org.tio.http.mcp.schema.*;
import org.tio.http.mcp.server.McpServer;
import org.tio.http.mcp.server.McpServerSession;
import org.tio.server.TioServerConfig;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.json.JsonUtil;
import org.tio.utils.mica.PayloadEncode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * mqtt mcp 接口
 *
 * @author L.cm
 */
public class MqttMcp {
	private final MqttServerCreator serverCreator;
	private final TioServerConfig mqttServerConfig;
	private final McpServer mcpServer;

	public MqttMcp(MqttServerCreator serverCreator,
				   TioServerConfig mqttServerConfig) {
		this(serverCreator, mqttServerConfig, new McpServer());
	}

	public MqttMcp(MqttServerCreator serverCreator,
				   TioServerConfig mqttServerConfig,
				   McpServer mcpServer) {
		this.serverCreator = serverCreator;
		this.mqttServerConfig = mqttServerConfig;
		this.mcpServer = mcpServer;
	}

	public McpTool getMqttStatsMcpTool() {
		String jsonSchema = "{\n" +
			"  \"type\": \"object\",\n" +
			"  \"description\": \"统计信息汇总VO，包含连接、消息和节点的统计信息\",\n" +
			"  \"properties\": {\n" +
			"    \"connections\": {\n" +
			"      \"type\": \"object\",\n" +
			"      \"description\": \"连接相关统计信息\",\n" +
			"      \"properties\": {\n" +
			"        \"accepted\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"共接受过的连接数\"\n" +
			"        },\n" +
			"        \"size\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"当前连接数\"\n" +
			"        },\n" +
			"        \"closed\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"关闭过的连接数\"\n" +
			"        }\n" +
			"      }\n" +
			"    },\n" +
			"    \"messages\": {\n" +
			"      \"type\": \"object\",\n" +
			"      \"description\": \"消息相关统计信息\",\n" +
			"      \"properties\": {\n" +
			"        \"handledPackets\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"处理的包数量\"\n" +
			"        },\n" +
			"        \"handledBytes\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"处理的消息字节数\"\n" +
			"        },\n" +
			"        \"receivedPackets\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"接收的包数量\"\n" +
			"        },\n" +
			"        \"receivedBytes\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"接收的字节数\"\n" +
			"        },\n" +
			"        \"sendPackets\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"发送的包数量\"\n" +
			"        },\n" +
			"        \"sendBytes\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"发送的字节数\"\n" +
			"        },\n" +
			"        \"bytesPerTcpReceive\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"平均每次TCP包接收的字节数\"\n" +
			"        },\n" +
			"        \"packetsPerTcpReceive\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"平均每次TCP包接收的业务包数量\"\n" +
			"        }\n" +
			"      }\n" +
			"    },\n" +
			"    \"nodes\": {\n" +
			"      \"type\": \"object\",\n" +
			"      \"description\": \"节点相关统计信息\",\n" +
			"      \"properties\": {\n" +
			"        \"clientNodes\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"客户端节点数量\"\n" +
			"        },\n" +
			"        \"connections\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"连接数\"\n" +
			"        },\n" +
			"        \"users\": {\n" +
			"          \"type\": \"number\",\n" +
			"          \"description\": \"用户数\"\n" +
			"        }\n" +
			"      }\n" +
			"    }\n" +
			"  }\n" +
			"}";

		McpTool mcpTool = new McpTool();
		mcpTool.setName("getMqttStatus");
		mcpTool.setDescription("获取 mqtt 状态");
		mcpTool.setReturnDirect(true);

		McpJsonSchema jsonSchemaIn = new McpJsonSchema();
		jsonSchemaIn.setType("object");
		jsonSchemaIn.setProperties(new HashMap<>());
		jsonSchemaIn.setRequired(new ArrayList<>());
		mcpTool.setInputSchema(jsonSchemaIn);

		McpJsonSchema jsonSchemaOut = JsonUtil.readValue(jsonSchema, McpJsonSchema.class);
		mcpTool.setOutputSchema(jsonSchemaOut);
		return mcpTool;
	}

	/**
	 * 获取 mqtt 状态
	 * @param session McpServerSession
	 * @param params params
	 * @return McpCallToolResult
	 */
	public McpCallToolResult getMqttStats(McpServerSession session, Map<String, Object> params) {
		StatVo statVo = this.mqttServerConfig.getStat();
		McpCallToolResult toolResult = new McpCallToolResult();
		McpTextContent content = new McpTextContent(JsonUtil.toJsonString(statVo));
		toolResult.setContent(Collections.singletonList(content));
		toolResult.setStructuredContent(statVo);
		return toolResult;
	}

	public McpTool getMqttPublishMcpTool() {
		String jsonSchemaIn = "{\n" +
			"  \"type\": \"object\",\n" +
			"  \"description\": \"MQTT 消息发布\",\n" +
			"  \"properties\": {\n" +
			"    \"topic\": {\n" +
			"      \"type\": \"string\",\n" +
			"      \"description\": \"消息主题\"\n" +
			"    },\n" +
			"    \"payload\": {\n" +
			"      \"type\": \"string\",\n" +
			"      \"description\": \"消息正文\"\n" +
			"    },\n" +
			"    \"encoding\": {\n" +
			"      \"type\": \"string\",\n" +
			"      \"description\": \"消息正文编码方式（仅支持 plain 或 base64）\"\n" +
			"    },\n" +
			"    \"qos\": {\n" +
			"      \"type\": \"integer\",\n" +
			"      \"default\": 0,\n" +
			"      \"description\": \"QoS 等级（0-2）\"\n" +
			"    },\n" +
			"    \"retain\": {\n" +
			"      \"type\": \"boolean\",\n" +
			"      \"default\": false,\n" +
			"      \"description\": \"是否为保留消息\"\n" +
			"    }\n" +
			"  },\n" +
			"  \"required\": [\"topic\", \"payload\"]\n" +
			"}";

		McpTool mcpTool = new McpTool();
		mcpTool.setName("mqttPublish");
		mcpTool.setDescription("mqtt 消息发布");
		mcpTool.setReturnDirect(true);

		// 输入参数
		mcpTool.setInputSchema(JsonUtil.readValue(jsonSchemaIn, McpJsonSchema.class));
		// 输出参数
		McpJsonSchema jsonSchemaOut = new McpJsonSchema();
		jsonSchemaOut.setType("object");
		Map<String, Object> properties = new HashMap<>();

		Map<String, Object> result = new HashMap<>();
		result.put("type", "boolean");
		result.put("description", "mqtt 发布结果");

		properties.put("result", result);
		jsonSchemaOut.setProperties(properties);
		jsonSchemaOut.setRequired(Collections.singletonList("result"));
		mcpTool.setOutputSchema(jsonSchemaOut);
		return mcpTool;
	}

	/**
	 * 发布 mqtt 消息
	 * @param session McpServerSession
	 * @param params params
	 * @return McpCallToolResult
	 */
	public McpCallToolResult mqttPublish(McpServerSession session, Map<String, Object> params) {
		PublishForm publishForm = JsonUtil.convertValue(params, PublishForm.class);
		boolean result = sendPublish(publishForm);
		// 响应结果
		Map<String, Object> json = new HashMap<>(2);
		json.put("result", result);
		// mcp 结果
		McpCallToolResult toolResult = new McpCallToolResult();
		McpTextContent content = new McpTextContent(JsonUtil.toJsonString(json));
		toolResult.setContent(Collections.singletonList(content));
		toolResult.setStructuredContent(json);
		return toolResult;
	}

	private boolean sendPublish(PublishForm form) {
		String payload = form.getPayload();
		Message message = new Message();
		message.setMessageType(MessageType.HTTP_API);
		message.setClientId(form.getClientId());
		message.setTopic(form.getTopic());
		message.setQos(form.getQos());
		message.setRetain(form.isRetain());
		// payload 解码
		if (StrUtil.isNotBlank(payload)) {
			message.setPayload(PayloadEncode.decode(payload, form.getEncoding()));
		}
		return serverCreator.getMessageDispatcher().send(message);
	}

	/**
	 * 注册 mcp
	 */
	public void register() {
		// 注册路由
		MqttHttpRoutes.register(Method.GET, mcpServer.getSseEndpoint(), mcpServer::sseEndpoint);
		MqttHttpRoutes.register(Method.POST, mcpServer.getMessageEndpoint(), mcpServer::sseMessageEndpoint);
		// mcp 信息
		McpServerCapabilities serverCapabilities = new McpServerCapabilities();
		McpLoggingCapabilities logging = new McpLoggingCapabilities();
		serverCapabilities.setLogging(logging);
		McpPromptCapabilities prompts = new McpPromptCapabilities();
		prompts.setListChanged(false);
		serverCapabilities.setPrompts(prompts);
		McpResourceCapabilities resources = new McpResourceCapabilities();
		resources.setListChanged(false);
		resources.setSubscribe(false);
		serverCapabilities.setResources(resources);
		// 只暴露 tools
		McpToolCapabilities tools = new McpToolCapabilities();
		tools.setListChanged(true);
		serverCapabilities.setTools(tools);
		mcpServer.capabilities(serverCapabilities);
		// 注册 tools
		mcpServer.tool(getMqttStatsMcpTool(), this::getMqttStats);
		mcpServer.tool(getMqttPublishMcpTool(), this::mqttPublish);
	}

}
