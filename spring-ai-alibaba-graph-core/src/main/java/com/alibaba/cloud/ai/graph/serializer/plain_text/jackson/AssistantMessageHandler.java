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

import org.springframework.ai.content.Media;

import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import org.springframework.ai.chat.messages.AssistantMessage;

import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.deserializeMediaList;
import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.deserializeMetadata;
import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.serializeMediaList;
import static com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SerializationHelper.serializeMetadata;

public interface AssistantMessageHandler {

	enum Field {

		TEXT("text"), TOOL_CALLS("toolCalls"), MEDIA("media");

		final String name;

		Field(String name) {
			this.name = name;
		}

	}

	class Serializer extends StdSerializer<AssistantMessage> {

		public Serializer() {
			super(AssistantMessage.class);
		}

	@Override
	public void serialize(AssistantMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
		gen.writeStartObject();
		serializeFields(msg, gen, provider);
		gen.writeEndObject();
	}

	@Override
	public void serializeWithType(AssistantMessage msg, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
		WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(msg, JsonToken.START_OBJECT));
		serializeFields(msg, gen, provider);
		typeSer.writeTypeSuffix(gen, typeIdDef);
	}

	private void serializeFields(AssistantMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
		gen.writeStringField(Field.TEXT.name, msg.getText());

		gen.writeArrayFieldStart(Field.TOOL_CALLS.name);
		for (var toolCall : msg.getToolCalls()) {
			gen.writeStartObject();
			gen.writeStringField("id", toolCall.id());
			gen.writeStringField("name", toolCall.name());
			gen.writeStringField("type", toolCall.type());
			gen.writeStringField("arguments", toolCall.arguments());
			gen.writeEndObject();
		}
		gen.writeEndArray();

		serializeMetadata(gen, msg.getMetadata());
		// ★ 序列化多模态 media（模型输出图片等），防止 cloneState 深拷贝时丢失
		serializeMediaList(gen, msg.getMedia());
	}
}

	class Deserializer extends StdDeserializer<AssistantMessage> {

		protected Deserializer() {
			super(AssistantMessage.class);
		}

		@Override
		public AssistantMessage deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
			var mapper = (ObjectMapper) jsonParser.getCodec();
			ObjectNode node = mapper.readTree(jsonParser);

			var text = node.findValue(Field.TEXT.name).asText();
			var metadata = deserializeMetadata(mapper, node);
			var requestsNode = node.findValue(Field.TOOL_CALLS.name);
			// ★ 反序列化多模态 media
			List<Media> mediaList = deserializeMediaList(node);

			if (requestsNode.isNull() || requestsNode.isEmpty()) {
				return AssistantMessage.builder()
						.content(text)
						.properties(metadata)
						.media(mediaList)
						.build();
			}

			var requests = new LinkedList<AssistantMessage.ToolCall>();

			for (JsonNode requestNode : requestsNode) {
				var request = mapper.treeToValue(requestNode, new TypeReference<AssistantMessage.ToolCall>() {
				});

				requests.add(request);
			}

			return AssistantMessage.builder()
					.content(text)
					.properties(metadata)
					.toolCalls(requests)
					.media(mediaList)
					.build();
		}

	}

}
