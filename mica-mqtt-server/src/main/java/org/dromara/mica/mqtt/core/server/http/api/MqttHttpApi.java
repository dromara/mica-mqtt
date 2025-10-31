/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.core.server.http.api;

import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.http.api.code.ResultCode;
import org.dromara.mica.mqtt.core.server.http.api.form.BaseForm;
import org.dromara.mica.mqtt.core.server.http.api.form.PublishForm;
import org.dromara.mica.mqtt.core.server.http.api.form.SubscribeForm;
import org.dromara.mica.mqtt.core.server.http.api.result.Result;
import org.dromara.mica.mqtt.core.server.http.handler.MqttHttpRoutes;
import org.dromara.mica.mqtt.core.server.model.ClientInfo;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.http.common.HttpConst;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.http.common.Method;
import org.tio.http.mcp.server.McpServerSession;
import org.tio.http.sse.SseEmitter;
import org.tio.server.TioServerConfig;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.json.JsonUtil;
import org.tio.utils.mica.PayloadEncode;
import org.tio.utils.timer.TimerTask;
import org.tio.utils.timer.TimerTaskService;

import java.util.List;
import java.util.function.Function;

/**
 * mqtt http api
 *
 * @author L.cm
 */
public class MqttHttpApi {
	private static final Logger log = LoggerFactory.getLogger(MqttHttpApi.class);
	private final MqttServerCreator serverCreator;
	private final TioServerConfig mqttServerConfig;

	public MqttHttpApi(MqttServerCreator serverCreator, TioServerConfig mqttServerConfig) {
		this.serverCreator = serverCreator;
		this.mqttServerConfig = mqttServerConfig;
	}

	/**
	 * 获取 api 列表
	 * <p>
	 * GET /api/v1/endpoints
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse endpoints(HttpRequest request) {
		return Result.ok(request, MqttHttpRoutes.getRouts().keySet());
	}

	/**
	 * 获取统计信息
	 * <p>
	 * GET /api/v1/stats
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse stats(HttpRequest request) {
		return Result.ok(request, this.mqttServerConfig.getStat());
	}

	/**
	 * 获取统计信息（sse 流）
	 * <p>
	 * GET /api/v1/stats/sse
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse statsSse(HttpRequest request) {
		// sse 频率，默认 3s
		int delay = request.getInt("delay", 3000);
		HttpResponse httpResponse = new HttpResponse(request);
		SseEmitter emitter = SseEmitter.getEmitter(request, httpResponse);
		// 响应包发送后，再发送 sse 回包
		httpResponse.setPacketListener((context, packet, isSentSuccess) -> {
			if (isSentSuccess) {
				TimerTaskService taskService = mqttServerConfig.getTaskService();
				if (taskService != null) {
					taskService.addTask((systemTimer -> new TimerTask(delay) {
						@Override
						public void run() {
							try {
								if (context != null && !context.isClosed()) {
									// 1. 再次添加 任务
									systemTimer.add(this);
									// 2. 执行任务
									emitter.send(JsonUtil.toJsonString(mqttServerConfig.getStat()));
								}
							} catch (Exception e) {
								log.error("Tio client schedule error", e);
							}
						}
					}));
				}
			}
		});
		return httpResponse;
	}

	/**
	 * 消息发布
	 * <p>
	 * POST /api/v1/mqtt/publish
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse publish(HttpRequest request) {
		PublishForm form = readForm(request, (requestBody) ->
			JsonUtil.readValue(requestBody, PublishForm.class)
		);
		if (form == null) {
			return Result.fail(request, ResultCode.E101);
		}
		// 表单校验
		HttpResponse validResponse = validForm(false, form, request);
		if (validResponse != null) {
			return validResponse;
		}
		sendPublish(form);
		return Result.ok();
	}

	/**
	 * 消息批量发布
	 * <p>
	 * POST /api/v1/mqtt/publish/batch
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse publishBatch(HttpRequest request) {
		List<PublishForm> formList = readForm(request, (requestBody) ->
			JsonUtil.readList(requestBody, PublishForm.class)
		);
		if (formList == null || formList.isEmpty()) {
			return Result.fail(request, ResultCode.E101);
		}
		// 参数校验，保证一个批次同时不成功，所以先校验
		for (PublishForm form : formList) {
			// 表单校验
			HttpResponse validResponse = validForm(false, form, request);
			if (validResponse != null) {
				return validResponse;
			}
		}
		// 批量发送
		for (PublishForm form : formList) {
			sendPublish(form);
		}
		return Result.ok();
	}

	private void sendPublish(PublishForm form) {
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
		serverCreator.getMessageDispatcher().send(message);
	}

	/**
	 * 主题订阅
	 * <p>
	 * POST /api/v1/mqtt/subscribe
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse subscribe(HttpRequest request) {
		SubscribeForm form = readForm(request, (requestBody) ->
			JsonUtil.readValue(requestBody, SubscribeForm.class)
		);
		if (form == null) {
			return Result.fail(request, ResultCode.E101);
		}
		// 表单校验
		HttpResponse validResponse = validForm(true, form, request);
		if (validResponse != null) {
			return validResponse;
		}
		int qos = form.getQos();
		if (qos < 0 || qos > 2) {
			return Result.fail(request, ResultCode.E101);
		}
		// 接口手动添加的订阅关系，可用来调试，不建议其他场景使用
		sendSubOrUnSubscribe(form);
		return Result.ok();
	}

	/**
	 * 主题批量订阅
	 * <p>
	 * POST /api/v1/mqtt/subscribe/batch
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse subscribeBatch(HttpRequest request) {
		List<SubscribeForm> formList = readForm(request, (requestBody) ->
			JsonUtil.readList(requestBody, SubscribeForm.class)
		);
		if (formList == null || formList.isEmpty()) {
			return Result.fail(request, ResultCode.E101);
		}
		// 参数校验，保证一个批次同时不成功，所以先校验
		for (SubscribeForm form : formList) {
			// 表单校验
			HttpResponse validResponse = validForm(true, form, request);
			if (validResponse != null) {
				return validResponse;
			}
			int qos = form.getQos();
			if (qos < 0 || qos > 2) {
				return Result.fail(request, ResultCode.E101);
			}
		}
		// 批量处理
		for (SubscribeForm form : formList) {
			// 接口手动添加的订阅关系，可用来调试，不建议其他场景使用
			sendSubOrUnSubscribe(form);
		}
		return Result.ok();
	}

	/**
	 * 取消订阅
	 * <p>
	 * POST /api/v1/mqtt/unsubscribe
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse unsubscribe(HttpRequest request) {
		BaseForm form = readForm(request, (requestBody) ->
			JsonUtil.readValue(requestBody, BaseForm.class)
		);
		if (form == null) {
			return Result.fail(request, ResultCode.E101);
		}
		// 表单校验
		HttpResponse validResponse = validForm(true, form, request);
		if (validResponse != null) {
			return validResponse;
		}
		// 接口手动取消的订阅关系，可用来调试，不建议其他场景使用
		sendSubOrUnSubscribe(form);
		return Result.ok();
	}

	/**
	 * 批量取消订阅
	 * <p>
	 * POST /api/v1/mqtt/unsubscribe/batch
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse unsubscribeBatch(HttpRequest request) {
		List<BaseForm> formList = readForm(request, (requestBody) ->
			JsonUtil.readList(requestBody, BaseForm.class)
		);
		if (formList == null || formList.isEmpty()) {
			return Result.fail(request, ResultCode.E101);
		}
		// 参数校验，保证一个批次同时不成功，所以先校验
		for (BaseForm form : formList) {
			// 表单校验
			HttpResponse validResponse = validForm(true, form, request);
			if (validResponse != null) {
				return validResponse;
			}
		}
		// 批量处理
		for (BaseForm form : formList) {
			// 接口手动添加的订阅关系，可用来调试，不建议其他场景使用
			sendSubOrUnSubscribe(form);
		}
		return Result.ok();
	}

	/**
	 * 获取取客户端信息
	 *
	 * <p>
	 * GET /api/v1/clients/info
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse getClientInfo(HttpRequest request) {
		String clientId = request.getParam("clientId");
		if (StrUtil.isBlank(clientId)) {
			return Result.fail(request, ResultCode.E101);
		}
		ChannelContext context = Tio.getByBsId(this.mqttServerConfig, clientId);
		if (context == null) {
			return Result.fail(request, ResultCode.E101);
		}
		ClientInfo clientInfo = ClientInfo.form(serverCreator, context);
		return Result.ok(request, clientInfo);
	}

	/**
	 * 分页拉取客户端列表
	 *
	 * <p>
	 * GET /api/v1/clients?_page=1&amp;_limit=10
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse getClients(HttpRequest request) {
		int page = request.getInt("_page", 1);
		int limit = request.getInt("_limit", 10000);
		return Result.ok(request, MqttServer.getClients(serverCreator, mqttServerConfig, page, limit));
	}

	/**
	 * 踢除指定客户端。注意踢除客户端操作会将连接与会话一并终结。
	 * <p>
	 * POST /api/v1/clients/delete
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse deleteClients(HttpRequest request) {
		String clientId = request.getParam("clientId");
		if (StrUtil.isBlank(clientId)) {
			return Result.fail(request, ResultCode.E101);
		}
		Message message = new Message();
		message.setClientId(clientId);
		message.setMessageType(MessageType.DISCONNECT);
		serverCreator.getMessageDispatcher().send(message);
		return Result.ok();
	}

	/**
	 * 获取客户端订阅情况
	 * <p>
	 * GET /api/v1/client/subscriptions
	 *
	 * @param request HttpRequest
	 * @return HttpResponse
	 */
	public HttpResponse getClientSubscriptions(HttpRequest request) {
		String clientId = request.getParam("clientId");
		if (StrUtil.isBlank(clientId)) {
			return Result.fail(request, ResultCode.E101);
		}
		List<Subscribe> subscribeList = serverCreator.getSessionManager().getSubscriptions(clientId);
		return Result.ok(new HttpResponse(request), subscribeList);
	}

	private void sendSubOrUnSubscribe(BaseForm form) {
		Message message = new Message();
		message.setFromClientId(form.getClientId());
		message.setTopic(form.getTopic());
		if (form instanceof SubscribeForm) {
			message.setQos(((SubscribeForm) form).getQos());
			message.setMessageType(MessageType.SUBSCRIBE);
		} else {
			message.setMessageType(MessageType.UNSUBSCRIBE);
		}
		serverCreator.getMessageDispatcher().send(message);
	}

	/**
	 * 读取表单
	 *
	 * @param request  HttpRequest
	 * @param function Function
	 * @param <T>      泛型
	 * @return 表单
	 */
	private static <T> T readForm(HttpRequest request, Function<String, T> function) {
		byte[] requestBody = request.getBody();
		if (requestBody == null) {
			return null;
		}
		return function.apply(new String(requestBody, HttpConst.CHARSET));
	}

	/**
	 * 校验表单
	 *
	 * @param isTopicFilter isTopicFilter
	 * @param form          BaseForm
	 * @param request       HttpRequest
	 * @return 表单
	 */
	private static HttpResponse validForm(boolean isTopicFilter, BaseForm form, HttpRequest request) {
		// 必须的参数
		String clientId = form.getClientId();
		if (StrUtil.isBlank(clientId)) {
			return Result.fail(request, ResultCode.E101);
		}
		String topic = form.getTopic();
		if (StrUtil.isBlank(topic)) {
			return Result.fail(request, ResultCode.E101);
		}
		try {
			if (isTopicFilter) {
				TopicUtil.validateTopicFilter(topic);
			} else {
				TopicUtil.validateTopicName(topic);
			}
		} catch (IllegalArgumentException exception) {
			return Result.fail(request, ResultCode.E102);
		}
		return null;
	}

	/**
	 * 注册路由
	 */
	public void register() {
		// @formatter:off
		MqttHttpRoutes.register(Method.GET, "/api/v1/endpoints", this::endpoints);
		MqttHttpRoutes.register(Method.GET, "/api/v1/stats", this::stats);
		MqttHttpRoutes.register(Method.GET, "/api/v1/stats/sse", this::statsSse);
		MqttHttpRoutes.register(Method.POST, "/api/v1/mqtt/publish", this::publish);
		MqttHttpRoutes.register(Method.POST, "/api/v1/mqtt/publish/batch", this::publishBatch);
		MqttHttpRoutes.register(Method.POST, "/api/v1/mqtt/subscribe", this::subscribe);
		MqttHttpRoutes.register(Method.POST, "/api/v1/mqtt/subscribe/batch", this::subscribeBatch);
		MqttHttpRoutes.register(Method.POST, "/api/v1/mqtt/unsubscribe", this::unsubscribe);
		MqttHttpRoutes.register(Method.POST, "/api/v1/mqtt/unsubscribe/batch", this::unsubscribeBatch);
		MqttHttpRoutes.register(Method.GET, "/api/v1/clients/info", this::getClientInfo);
		MqttHttpRoutes.register(Method.GET, "/api/v1/clients", this::getClients);
		MqttHttpRoutes.register(Method.POST, "/api/v1/clients/delete", this::deleteClients);
		MqttHttpRoutes.register(Method.GET, "/api/v1/client/subscriptions", this::getClientSubscriptions);
		// @formatter:on
	}

}
