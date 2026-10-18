package shipeditor.persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

import java.io.IOException;

public class BasicPrettyPrinter extends DefaultPrettyPrinter {

    public static final String LINEFEED = DefaultIndenter.SYS_LF;
    private static final char ARRAY_END = ']';

    public static final String INDENT = "    ";

    /**
     * Writes a raw line feed followed by the given number of indentation levels.
     */
    public static void writeLineFeedAndIndent(JsonGenerator gen, int levels) throws IOException {
        gen.writeRaw(LINEFEED);
        writeIndentation(gen, levels);
    }

    /**
     * Writes the given number of raw indentation levels.
     */
    public static void writeIndentation(JsonGenerator gen, int levels) throws IOException {
        for (int i = 0; i < levels; i++) {
            gen.writeRaw(INDENT);
        }
    }

    @Override
    public BasicPrettyPrinter createInstance() {
        BasicPrettyPrinter basicPrettyPrinter = new BasicPrettyPrinter();

        DefaultIndenter indenter = new DefaultIndenter(BasicPrettyPrinter.INDENT, BasicPrettyPrinter.LINEFEED);
        basicPrettyPrinter.indentArraysWith(indenter);
        basicPrettyPrinter.indentObjectsWith(indenter);

        return basicPrettyPrinter;
    }

    @Override
    public void writeEndArray(JsonGenerator g, int nrOfValues) throws IOException {
        if (!_arrayIndenter.isInline()) {
            --_nesting;
        }
        if (nrOfValues > 0) {
            _arrayIndenter.writeIndentation(g, _nesting);
        }
        g.writeRaw(ARRAY_END);
    }

    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator g) throws IOException {
        g.writeRaw(_separators.getObjectFieldValueSeparator());
        g.writeRaw("");
    }

}
