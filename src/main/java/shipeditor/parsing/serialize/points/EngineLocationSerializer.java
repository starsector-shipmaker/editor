package shipeditor.parsing.serialize.points;

import com.fasterxml.jackson.core.JsonGenerator;
import shipeditor.persistence.BasicPrettyPrinter;

import java.io.IOException;

public class EngineLocationSerializer extends Point2DSerializer {

    @Override
    protected void writeClosingIndentation(JsonGenerator gen) throws IOException {
        super.writeClosingIndentation(gen);
        BasicPrettyPrinter.writeIndentation(gen, 2);
    }

}
