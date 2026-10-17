package shipeditor.parsing.deserialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import shipeditor.representation.RepresentationEnums.ShipTypeHints;

@lombok.extern.log4j.Log4j2
public class CustomDeserializers {


    public static class ColorArrayRGBADeserializer extends JsonDeserializer<Color> {

        @Override
        public Color deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            try {
                int[] rgbaValues = p.readValueAs(int[].class);
                if (rgbaValues == null) {
                    return new Color(0, 0, 0, 0);
                }
                if (rgbaValues.length < 3) {
                    return Color.WHITE;
                }

                int red = Math.max(0, Math.min(255, rgbaValues[0]));
                int green = Math.max(0, Math.min(255, rgbaValues[1]));
                int blue = Math.max(0, Math.min(255, rgbaValues[2]));
                int alpha = rgbaValues.length >= 4 ? Math.max(0, Math.min(255, rgbaValues[3])) : 255;

                return new Color(red, green, blue, alpha);
            } catch (Exception e) {
                log.error("Failed to deserialize RGBA color array", e);
                return Color.WHITE;
            }
        }

    }


    @lombok.extern.log4j.Log4j2
    public static class ModulesDeserializer extends JsonDeserializer<Map<String, String>> {

        @Override
        public Map<String, String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            ObjectCodec codec = p.getCodec();
            JsonNode node = codec.readTree(p);
            Map<String, String> moduleMap = new LinkedHashMap<>();

            if (node == null) {
                return moduleMap;
            }

            try {
                if (node.isArray()) {
                    for (JsonNode entry : node) {
                        if (entry.isObject()) {
                            entry.fields().forEachRemaining(field -> {
                                moduleMap.put(field.getKey(), field.getValue().asText());
                            });
                        }
                    }
                } else if (node.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> entryIterator = node.fields();
                    entryIterator.forEachRemaining(entry -> {
                        JsonNode entryValue = entry.getValue();
                        moduleMap.put(entry.getKey(), entryValue.asText());
                    });
                }
            } catch (Exception e) {
                log.error("Failed to parse modules node", e);
            }

            return moduleMap;
        }
}


    public static class TextureTypeDeserializer extends JsonDeserializer<List<String>> {

        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.isExpectedStartArrayToken() || p.currentToken() == JsonToken.START_ARRAY) {
                List<String> list = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    list.add(p.getValueAsString());
                }
                return list;
            } else if (p.isExpectedStartObjectToken() || p.currentToken() == JsonToken.START_OBJECT) {
                p.skipChildren(); // Skip the object entirely
                return new ArrayList<>();
            } else {
                String singleValue = p.getValueAsString();
                List<String> list = new ArrayList<>();
                if (singleValue != null) {
                    list.add(singleValue);
                }
                return list;
            }
        }
    }

    @lombok.extern.log4j.Log4j2
    public static class Point2DDeserializer extends JsonDeserializer<Point2D.Double> {

        @Override
        public Point2D.Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            try {
                ObjectCodec codec = p.getCodec();
                JsonNode node = codec.readTree(p);
                if (node == null || !node.isArray() || node.size() < 2) {
                    return new Point2D.Double(0, 0);
                }
                JsonNode xNode = node.get(0);
                JsonNode yNode = node.get(1);
                double x = xNode != null ? xNode.asDouble() : 0.0;
                double y = yNode != null ? yNode.asDouble() : 0.0;
                return new Point2D.Double(x, y);
            } catch (Exception e) {
                log.error("Failed to deserialize Point2D", e);
                return new Point2D.Double(0, 0);
            }
        }
    }


    @lombok.extern.log4j.Log4j2
    public static class ShipTypeHintsDeserializer extends JsonDeserializer<List<ShipTypeHints>> {

    @Override
    public List<ShipTypeHints> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<ShipTypeHints> hints = new ArrayList<>();

        if (p.currentToken() == JsonToken.START_ARRAY) {
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken() == JsonToken.VALUE_STRING) {
                    String enumString = p.getText();
                    if (enumString != null && !enumString.isBlank()) {
                        try {
                            ShipTypeHints hint = ShipTypeHints.valueOf(enumString.trim());
                            hints.add(hint);
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid ShipTypeHint encountered and ignored: {}", enumString);
                        }
                    }
                }
            }
        }

        return hints;
    }

}


    @lombok.extern.log4j.Log4j2
    public static class Point2DArrayDeserializer extends JsonDeserializer<Point2D[]> {

        @Override
        public Point2D.Double[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            try {
                ObjectCodec codec = p.getCodec();
                JsonNode node = codec.readTree(p);
                if (node == null || !node.isArray()) {
                    return new Point2D.Double[0];
                }
                
                if (node.size() > 0 && node.get(0) != null && node.get(0).isNumber()) {
                    int size = node.size() / 2;
                    Point2D.Double[] points = new Point2D.Double[size];
                    for (int i = 0; i < size; i++) {
                        JsonNode xNode = node.get(i * 2);
                        JsonNode yNode = node.get(i * 2 + 1);
                        double x = xNode != null ? xNode.asDouble() : 0.0;
                        double y = yNode != null ? yNode.asDouble() : 0.0;
                        points[i] = new Point2D.Double(x, y);
                    }
                    return points;
                } else {
                    int size = node.size();
                    Point2D.Double[] points = new Point2D.Double[size];
                    for (int i = 0; i < size; i++) {
                        JsonNode pointNode = node.get(i);
                        if (pointNode != null && pointNode.isArray() && pointNode.size() >= 2) {
                            double x = pointNode.get(0) != null ? pointNode.get(0).asDouble() : 0.0;
                            double y = pointNode.get(1) != null ? pointNode.get(1).asDouble() : 0.0;
                            points[i] = new Point2D.Double(x, y);
                        } else {
                            points[i] = new Point2D.Double(0, 0);
                        }
                    }
                    return points;
                }
            } catch (Exception e) {
                log.error("Failed to deserialize Point2DArray", e);
                return new Point2D.Double[0];
            }
        }
    }

}
