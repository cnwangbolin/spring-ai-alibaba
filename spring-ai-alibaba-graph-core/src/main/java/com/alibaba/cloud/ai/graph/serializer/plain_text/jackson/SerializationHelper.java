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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

class SerializationHelper {

	static final String METADATA_FIELD = "metadata";

	static final String MEDIA_FIELD = "media";

	static Map<String, Object> deserializeMetadata(ObjectMapper mapper, JsonNode parentNode)
			throws JsonProcessingException {
		if (parentNode == null) {
			return Map.of();
		}

		var node = parentNode.findValue(METADATA_FIELD);

		if (node == null || node.isNull() || node.isEmpty()) {
			return Map.of();
		}
		if (!node.isObject()) {
			throw new IllegalStateException("Metadata must be an object");
		}
		return mapper.treeToValue(node, new TypeReference<>() {
		});
	}

	static void serializeMetadata(JsonGenerator gen, Map<String, Object> metadata) throws IOException {
		gen.writeObjectField(METADATA_FIELD, metadata);
	}

	/**
	 * 序列化多模态 media 列表（图片等），防止 cloneState 深拷贝时丢失。
	 * <p>
	 * 每个 Media 序列化为 {mimeType, name, id, data(base64)|url}。
	 * 优先用 base64 序列化字节数据（适用于 ByteArrayResource），
	 * 字节读取失败时降级为 URL（适用于 URI 类型的 Media）。
	 * </p>
	 */
	static void serializeMediaList(JsonGenerator gen, List<Media> mediaList) throws IOException {
		if (mediaList == null || mediaList.isEmpty()) {
			return;
		}
		gen.writeArrayFieldStart(MEDIA_FIELD);
		for (Media media : mediaList) {
			gen.writeStartObject();
			if (media.getMimeType() != null) {
				gen.writeStringField("mimeType", media.getMimeType().toString());
			}
			if (media.getName() != null) {
				gen.writeStringField("name", media.getName());
			}
			if (media.getId() != null) {
				gen.writeStringField("id", media.getId());
			}
			try {
				byte[] bytes = media.getDataAsByteArray();
				if (bytes != null && bytes.length > 0) {
					gen.writeStringField("data", Base64.getEncoder().encodeToString(bytes));
				}
			}
			catch (Exception e) {
				Object data = media.getData();
				if (data instanceof URI uri) {
					gen.writeStringField("url", uri.toString());
				}
			}
			gen.writeEndObject();
		}
		gen.writeEndArray();
	}

	/**
	 * 反序列化多模态 media 列表，从 base64 字节或 URL 恢复 Media 对象。
	 */
	static List<Media> deserializeMediaList(JsonNode parentNode) {
		List<Media> mediaList = new ArrayList<>();
		if (parentNode == null) {
			return mediaList;
		}
		var mediaArrayNode = parentNode.findValue(MEDIA_FIELD);
		if (mediaArrayNode == null || !mediaArrayNode.isArray() || mediaArrayNode.isEmpty()) {
			return mediaList;
		}
		for (JsonNode mediaNode : mediaArrayNode) {
			try {
				String mimeTypeStr = mediaNode.has("mimeType") ? mediaNode.get("mimeType").asText()
						: "application/octet-stream";
				MimeType mimeType = MimeType.valueOf(mimeTypeStr);
				Media media;
				if (mediaNode.has("data")) {
					byte[] bytes = Base64.getDecoder().decode(mediaNode.get("data").asText());
					media = new Media(mimeType, new ByteArrayResource(bytes));
				}
				else if (mediaNode.has("url")) {
					media = new Media(mimeType, URI.create(mediaNode.get("url").asText()));
				}
				else {
					continue;
				}
				mediaList.add(media);
			}
			catch (Exception e) {
				// 单个 media 反序列化失败不影响其他 media 和消息本身
			}
		}
		return mediaList;
	}

}
