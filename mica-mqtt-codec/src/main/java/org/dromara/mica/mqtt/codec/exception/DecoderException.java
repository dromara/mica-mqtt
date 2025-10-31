/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
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

package org.dromara.mica.mqtt.codec.exception;

/**
 * 解码异常
 *
 * @author L.cm
 */
public class DecoderException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public DecoderException() {
	}

	public DecoderException(String message) {
		super(message);
	}

	public DecoderException(String message, Throwable cause) {
		super(message, cause);
	}

	public DecoderException(Throwable cause) {
		super(cause);
	}

	public DecoderException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
