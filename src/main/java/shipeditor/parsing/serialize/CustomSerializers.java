package shipeditor.parsing.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import shipeditor.utility.Utility;

public class CustomSerializers {


    public static class ColorArrayRGBASerializer extends StdSerializer<Color> {

    public ColorArrayRGBASerializer() {
        super(Color.class);
    }

    protected ColorArrayRGBASerializer(Class<Color> t) {
        super(t);
    }

    @Override
    public void serialize(Color value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartArray();
        gen.writeNumber(value.getRed());
        gen.writeNumber(value.getGreen());
        gen.writeNumber(value.getBlue());
        gen.writeNumber(value.getAlpha());
        gen.writeEndArray();
    }

}


    public static class BaseNumberSerializer extends JsonSerializer<Double> {

    @Override
    public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value % 1 == 0) {
            gen.writeNumber(value.intValue());
        } else {
            gen.writeNumber(value);
        }
    }

}


    public static class SerializationUtilities {

    private SerializationUtilities() {
    }

    public static void writePoint2DForArray(Point2D point, JsonGenerator gen) throws IOException {
        double pointX = point.getX();
        double pointY = point.getY();

        String resultX = Utility.formatDouble(pointX);
        String resultY = Utility.formatDouble(pointY);

        gen.writeRaw(resultX);
        gen.writeRaw(", " + resultY);
    }

    public static void writePoint2DForSingle(Point2D point, JsonGenerator gen) throws IOException {
        String resultX = formatIntegralAsInt(point.getX());
        String resultY = formatIntegralAsInt(point.getY());

        gen.writeRaw(resultX);
        gen.writeRaw(", " + resultY);
    }

    private static String formatIntegralAsInt(double value) {
        if (value % 1 == 0) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

}

}
