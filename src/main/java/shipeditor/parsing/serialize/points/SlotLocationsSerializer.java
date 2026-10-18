package shipeditor.parsing.serialize.points;

import com.fasterxml.jackson.core.JsonGenerator;
import shipeditor.persistence.BasicPrettyPrinter;

import java.io.IOException;

public class SlotLocationsSerializer extends Point2DArraySerializer {

    @Override
    protected void writeClosure(JsonGenerator gen, int length) throws IOException {
        BasicPrettyPrinter.writeLineFeedAndIndent(gen, 3);
    }

}
