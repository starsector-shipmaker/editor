package shipeditor.parsing.saving;

import shipeditor.utility.text.StringManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.viewer.layers.weapon.ProjectileLayer;
import shipeditor.parsing.FileUtilities;
import shipeditor.representation.weapon.ProjectileSpecFile;
import shipeditor.utility.components.ToastManager;
import shipeditor.utility.overseers.StaticController;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Log4j2
final class SaveProjectileAction {

    private SaveProjectileAction() {
    }

    static void saveProjectileFromLayer(ProjectileLayer projectileLayer) {
        JFileChooser fileChooser = FileUtilities.getFileChooser();

        File currentDirectory = fileChooser.getCurrentDirectory();
        ProjectileSpecFile projectileSpecFile = projectileLayer.getSpecFile();
        if (projectileSpecFile == null) return;
        
        File initial = new File(currentDirectory, projectileSpecFile.getId() != null ? projectileSpecFile.getId() + ".proj" : "new_projectile.proj");
        fileChooser.setSelectedFile(initial);

        Path specFilePath = projectileSpecFile.getProjectileSpecFilePath();
        if (specFilePath != null) {
            File originalPath = specFilePath.toFile();
            if (originalPath.isFile()) {
                fileChooser.setSelectedFile(originalPath);
            }
        }

        int returnVal = fileChooser.showSaveDialog(shipeditor.PrimaryWindow.getInstance());
        File lastDirectory = fileChooser.getCurrentDirectory();
        FileUtilities.setLastGeneralDirectory(lastDirectory);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File result = fileChooser.getSelectedFile();
            if (!result.getName().endsWith(".proj")) {
                result = new File(result.getParentFile(), result.getName() + ".proj");
            }

            log.info("Commencing projectile saving: {}", result);

            ObjectMapper objectMapper = FileUtilities.getConfigured();
            String errorMessage = "Projectile file saving failed: {}";
            
            try {
                SaveActionUtilities.mergeUnrecognizedProperties(result, projectileSpecFile.getUnrecognizedProperties(), ProjectileSpecFile.class);

                projectileSpecFile.setProjectileSpecFilePath(result.toPath());
                objectMapper.writeValue(result, projectileSpecFile);
                
                StaticController.getViewer().getLayerManager().markSaved(projectileLayer, "projectile");
                ToastManager.show("Saved " + result.getName() + " to " + result, ToastManager.ToastLevel.SUCCESS);
            } catch (IOException e) {
                log.error(errorMessage, result.getName(), e);
                JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                        StringManager.getString("PROJECTILE_FILE_SAVING_FAILED_EXCEPTION_MSG") + result,
                        StringManager.getString("FILE_SAVING_ERROR"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
