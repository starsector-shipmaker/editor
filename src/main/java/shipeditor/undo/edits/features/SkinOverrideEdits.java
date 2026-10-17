package shipeditor.undo.edits.features;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.undo.AbstractEdit;
import shipeditor.utility.UtilityEnums;
import shipeditor.utility.overseers.StaticController;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Log4j2
public final class SkinOverrideEdits {

    private SkinOverrideEdits() {}

    public static class SkinMapOverrideEdit<K, V> extends AbstractEdit {
        private final Consumer<Map<K, V>> setter;
        private final Map<K, V> oldMap;
        private final Map<K, V> newMap;
        private final EditorInstrument editorMode;
        private final ShipSkin skin;

        public SkinMapOverrideEdit(Consumer<Map<K, V>> setter, Map<K, V> oldMap, Map<K, V> newMap, EditorInstrument editorMode, ShipSkin skin) {
            this.setter = setter;
            this.oldMap = oldMap;
            this.newMap = newMap;
            this.editorMode = editorMode;
            this.skin = skin;
        }

        @Override
        public void undo() {
            setter.accept(oldMap);
            refreshUI();
        }

        @Override
        public void redo() {
            setter.accept(newMap);
            refreshUI();
        }

        private void refreshUI() {
            skin.invalidateBuiltIns();
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            EventBus.publish(new InstrumentRepaintQueued(editorMode));
        }

        @Override
        public String getName() {
            return "Modify Skin Overrides";
        }

        @Override
        public UtilityEnums.EditCategory getCategory() {
            return UtilityEnums.EditCategory.SKIN;
        }
    }

    public static class SkinListOverrideEdit<T> extends AbstractEdit {
        private final Consumer<List<T>> setter;
        private final List<T> oldList;
        private final List<T> newList;
        private final EditorInstrument editorMode;
        private final ShipSkin skin;

        public SkinListOverrideEdit(Consumer<List<T>> setter, List<T> oldList, List<T> newList, EditorInstrument editorMode, ShipSkin skin) {
            this.setter = setter;
            this.oldList = oldList;
            this.newList = newList;
            this.editorMode = editorMode;
            this.skin = skin;
        }

        @Override
        public void undo() {
            setter.accept(oldList);
            refreshUI();
        }

        @Override
        public void redo() {
            setter.accept(newList);
            refreshUI();
        }

        private void refreshUI() {
            skin.invalidateBuiltIns();
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueBuiltInsRepaint();
            EventBus.publish(new InstrumentRepaintQueued(editorMode));
        }

        @Override
        public String getName() {
            return "Modify Skin Removals";
        }

        @Override
        public UtilityEnums.EditCategory getCategory() {
            return UtilityEnums.EditCategory.SKIN;
        }
    }
}
