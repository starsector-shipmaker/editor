package shipeditor.parsing.serialize.points;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import shipeditor.parsing.serialize.CustomSerializers.SerializationUtilities;
import shipeditor.persistence.BasicPrettyPrinter;

import java.awt.geom.Point2D;
import java.io.IOException;

public class Point2DArraySerializer extends JsonSerializer<Point2D.Double[]> {

    @Override
    public void serialize(Point2D.Double[] value, JsonGenerator gen,
                          SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        if (value.length == 0) {
            gen.writeEndArray();
            return;
        }
        PrettyPrinter prettyPrinter = gen.getPrettyPrinter();
        prettyPrinter.beforeArrayValues(gen);

        int length = value.length;
        int count = 0;
        for (Point2D.Double point : value) {
            count++;
            boolean isLast = (count == length);

            SerializationUtilities.writePoint2DForArray(point, gen);

            if (isLast) {
                writeClosure(gen, length);
            } else {
                prettyPrinter.writeArrayValueSeparator(gen);
            }

            if (count % 5 == 0 && !isLast) {
                BasicPrettyPrinter.writeLineFeedAndIndent(gen, 2);
            }
        }
        gen.writeEndArray();
    }

    protected void writeClosure(JsonGenerator gen, int length) throws IOException {
        if (length > 10) {
            BasicPrettyPrinter.writeLineFeedAndIndent(gen, 1);
            gen.writeRaw("# " + length + " points total.");
        }

        BasicPrettyPrinter.writeLineFeedAndIndent(gen, 1);
    }

}
