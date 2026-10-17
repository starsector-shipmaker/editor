---
trigger: always_on
---

# Project Filetree Structure

This file contains the directory structure of the `src` directory to help understand package organization.

```yaml
structure:
  main:
    resources: "src/main/resources"
    packages:
      communication:
        - "shipeditor.communication"
        - "shipeditor.communication.events"
        - "shipeditor.communication.events.components"
        - "shipeditor.communication.events.files"
        - "shipeditor.communication.events.viewer"
        - "shipeditor.communication.events.viewer.control"
        - "shipeditor.communication.events.viewer.layers"
        - "shipeditor.communication.events.viewer.points"
      components:
        - "shipeditor.components"
        - "shipeditor.components.datafiles"
        - "shipeditor.components.datafiles.entities"
        - "shipeditor.components.datafiles.entities.transferable"
        - "shipeditor.components.datafiles.styles"
        - "shipeditor.components.datafiles.trees"
        - "shipeditor.components.dialogs"
        - "shipeditor.components.help"
        - "shipeditor.components.help.parts"
        - "shipeditor.components.instrument"
        - "shipeditor.components.instrument.projectile"
        - "shipeditor.components.instrument.ship"
        - "shipeditor.components.instrument.ship.bays"
        - "shipeditor.components.instrument.ship.bounds"
        - "shipeditor.components.instrument.ship.builtins"
        - "shipeditor.components.instrument.ship.builtins.hullmods"
        - "shipeditor.components.instrument.ship.builtins.wings"
        - "shipeditor.components.instrument.ship.centers"
        - "shipeditor.components.instrument.ship.engines"
        - "shipeditor.components.instrument.ship.hull"
        - "shipeditor.components.instrument.ship.shared"
        - "shipeditor.components.instrument.ship.skins"
        - "shipeditor.components.instrument.ship.slots"
        - "shipeditor.components.instrument.ship.variant"
        - "shipeditor.components.instrument.ship.variant.hullmods"
        - "shipeditor.components.instrument.ship.variant.modules"
        - "shipeditor.components.instrument.weapon"
        - "shipeditor.components.layering"
        - "shipeditor.components.logging"
        - "shipeditor.components.settings"
        - "shipeditor.components.viewer"
        - "shipeditor.components.viewer.control"
        - "shipeditor.components.viewer.entities"
        - "shipeditor.components.viewer.entities.bays"
        - "shipeditor.components.viewer.entities.engine"
        - "shipeditor.components.viewer.entities.weapon"
        - "shipeditor.components.viewer.layers"
        - "shipeditor.components.viewer.layers.ship"
        - "shipeditor.components.viewer.layers.ship.data"
        - "shipeditor.components.viewer.layers.weapon"
        - "shipeditor.components.viewer.painters"
        - "shipeditor.components.viewer.painters.points"
        - "shipeditor.components.viewer.painters.points.ship"
        - "shipeditor.components.viewer.painters.points.ship.features"
        - "shipeditor.components.viewer.painters.points.weapon"
      menubar:
        - "shipeditor.menubar"
      parsing:
        - "shipeditor.parsing"
        - "shipeditor.parsing.deserialize"
        - "shipeditor.parsing.loading"
        - "shipeditor.parsing.saving"
        - "shipeditor.parsing.serialize"
        - "shipeditor.parsing.serialize.points"
      persistence:
        - "shipeditor.persistence"
        - "shipeditor.persistence.database"
      representation:
        - "shipeditor.representation"
        - "shipeditor.representation.ship"
        - "shipeditor.representation.weapon"
        - "shipeditor.representation.weapon.animation"
      undo:
        - "shipeditor.undo"
        - "shipeditor.undo.edits"
        - "shipeditor.undo.edits.features"
        - "shipeditor.undo.edits.points"
        - "shipeditor.undo.edits.points.engines"
        - "shipeditor.undo.edits.points.slots"
      utility:
        - "shipeditor.utility"
        - "shipeditor.utility.components"
        - "shipeditor.utility.components.containers"
        - "shipeditor.utility.components.containers.trees"
        - "shipeditor.utility.components.dialog"
        - "shipeditor.utility.components.rendering"
        - "shipeditor.utility.components.widgets"
        - "shipeditor.utility.graphics"
        - "shipeditor.utility.graphics.opengl"
        - "shipeditor.utility.objects"
        - "shipeditor.utility.overseers"
        - "shipeditor.utility.text"
        - "shipeditor.utility.themes"
  test:
    packages:
      communication:
        - "shipeditor.communication"
      components:
        - "shipeditor.components.datafiles.trees"
        - "shipeditor.components.dialogs"
        - "shipeditor.components.viewer.layers"
      parsing:
        - "shipeditor.parsing"
        - "shipeditor.parsing.loading"
        - "shipeditor.parsing.serialize"
      persistence:
        - "shipeditor.persistence"
        - "shipeditor.persistence.database"
      representation:
        - "shipeditor.representation"
      ui:
        - "shipeditor.ui"
      undo:
        - "shipeditor.undo"
      utility:
        - "shipeditor.utility"
        - "shipeditor.utility.components.dialog"
        - "shipeditor.utility.components.widgets"
        - "shipeditor.utility.graphics"
        - "shipeditor.utility.text"
```
