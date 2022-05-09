package com.inforefiner.custom.util;

import org.apache.flink.calcite.shaded.com.fasterxml.jackson.core.JsonGenerator;
import org.apache.flink.calcite.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.apache.flink.calcite.shaded.com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.flink.calcite.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.calcite.shaded.com.fasterxml.jackson.databind.SerializationFeature;

import java.io.Serializable;

public class FlinkJsonBuilder implements Serializable {

    private static final long serialVersionUID = 1485188006142301611L;
    private final ObjectMapper mapper;

    private static FlinkJsonBuilder _instance = new FlinkJsonBuilder();

    public static FlinkJsonBuilder getInstance() {
        return _instance;
    }

    public FlinkJsonBuilder() {
        mapper = buildObjectMapper(false);
    }

    public FlinkJsonBuilder pretty() {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return this;
    }

    public <T> T fromJson(String json, Class<T> typeClass) {
        if (json == null) {
            throw new IllegalArgumentException("json string should not be null");
        }
        try {
            return mapper.readValue(json, typeClass);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null) {
            throw new IllegalArgumentException("json string should not be null");
        }
        try {
            return mapper.readValue(json, typeReference);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T fromObject(Object obj, Class<T> typeClass) {
        return fromJson(toJson(obj), typeClass);
    }

    public <T> T fromObject(Object obj, TypeReference<T> typeReference) {
        return fromJson(toJson(obj), typeReference);
    }

    public String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectMapper buildObjectMapper(boolean prettyJson) {
        ObjectMapper m = new ObjectMapper();
        if (prettyJson) {
            m.enable(SerializationFeature.INDENT_OUTPUT);
        }
//        m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//        m.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        m.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        return m;
    }
}
