/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph.serializer.plain_text.jackson;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.deserializeMediaList;
import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.deserializeMetadata;
import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.serializeMediaList;
import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.serializeMetadata;

public interface UserMessageHandler {

	enum Field {

		TEXT("text"), MEDIA("media");

		final String name;

		Field(String name) {
			this.name = name;
		}

	}

	class Serializer extends StdSerializer<UserMessage> {

		public Serializer() {
			super(UserMessage.class);
		}

		@Override
		public void serialize(UserMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeStartObject();
			serializeFields(msg, gen, provider);
			gen.writeEndObject();
		}

		@Override
		public void serializeWithType(UserMessage msg, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
			WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(msg, JsonToken.START_OBJECT));
			serializeFields(msg, gen, provider);
			typeSer.writeTypeSuffix(gen, typeIdDef);
		}

		/**
		 * ★ 序列化 UserMessage 全部字段，含多模态 media（图片等）。
		 * <p>
		 * 修复说明：原实现将 media 序列化代码注释掉，导致 cloneState 深拷贝
		 * （通过 JSON 序列化/反序列化）时多模态 UserMessage 的 media 丢失，
		 * 模型收不到图片。此处恢复 media 序列化。
		 * </p>
		 */
		private void serializeFields(UserMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeStringField(Field.TEXT.name, msg.getText());
			serializeMetadata(gen, msg.getMetadata());
			serializeMediaList(gen, msg.getMedia());
		}
	}

	class Deserializer extends StdDeserializer<UserMessage> {

		public Deserializer() {
			super(UserMessage.class);
		}

		/**
		 * ★ 反序列化 UserMessage，恢复多模态 media（图片等）。
		 */
		@Override
		public UserMessage deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
			var mapper = (ObjectMapper) jsonParser.getCodec();
			ObjectNode node = mapper.readTree(jsonParser);

			var text = node.findValue(Field.TEXT.name).asText();
			var metadata = deserializeMetadata(mapper, node);
			List<Media> mediaList = deserializeMediaList(node);

			if (mediaList.isEmpty()) {
				return UserMessage.builder().text(text).metadata(metadata).build();
			}
			return UserMessage.builder().text(text).metadata(metadata).media(mediaList).build();
		}

	}

}
