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

package org.dromara.mica.mqtt.broker.rule.loader;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * YAML 规则文件加载器。
 * <p>
 * 依赖 SnakeYAML，在 broker 模块中为 <b>可选依赖</b>：Spring Boot 应用已传递引入，
 * 纯 Java 用户需自行引入 {@code org.yaml:snakeyaml}。
 * </p>
 *
 * @author L.cm
 */
public class YamlRuleLoader extends AbstractFileRuleLoader {

	public YamlRuleLoader(Path path) {
		super(path);
	}

	public YamlRuleLoader(Path path, long watchIntervalMs) {
		super(path, watchIntervalMs);
	}

	@Override
	protected Object readConfig() throws IOException {
		try (InputStream in = Files.newInputStream(path)) {
			return new Yaml().load(in);
		}
	}
}
