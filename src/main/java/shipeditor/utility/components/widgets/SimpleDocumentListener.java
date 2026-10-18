package shipeditor.utility.components.widgets;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A {@link DocumentListener} that runs the same callback for every kind of document change.
 */
public record SimpleDocumentListener(Runnable callback) implements DocumentListener {

    @Override
    public void insertUpdate(DocumentEvent e) {
        callback.run();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        callback.run();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        callback.run();
    }
}
