# EventBus Event Creation & Subscription Template

### 1. Define the Event
Create a simple record class under `shipeditor.communication.events`:

```java
package shipeditor.communication.events.viewer.layers;

import shipeditor.communication.events.BusEvent;
import shipeditor.components.viewer.layers.ViewerLayer;

public record CustomLayerEvent(ViewerLayer layer, String action) implements BusEvent {
}
```

### 2. Subscribe (Typed or General Listener with Weak Parent)
Bind the subscriber to a parent Swing/AWT component lifecycle to prevent garbage collection while the parent is alive:

```java
// Preferred for single event types:
EventBus.subscribe(this, CustomLayerEvent.class, checked -> {
    handleCustomEvent(checked.layer(), checked.action());
});

// Or general listener for multiple event types:
EventBus.subscribe(this, event -> {
    if (event instanceof CustomLayerEvent checked) {
        handleCustomEvent(checked.layer(), checked.action());
    }
});
```

### 3. Clean Up Subscriptions
When a layer or component is explicitly removed or disposed:

```java
EventBus.unsubscribeByParent(this);
```
