package shipeditor.communication;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.events.BusEvent;
import shipeditor.utility.Errors;

import java.util.Set;
import java.util.function.Consumer;

/** * This is a very simple implementation of EventBus-type Observer pattern, meant to decouple different parts of the app.
 * <p>
 * Initially implemented in a much more elaborate form with generics and selective dispatch by class metadata.
 * Yet, excessive complexity here only leads to more issues down the road; best to KISS here.*/
@Log4j2
public final class EventBus {

    private static final EventBus EVENT_BUS = new EventBus();

    // Floating subscribers: the listener itself is the weak key. It will be GC'd if not strongly referenced elsewhere.
    private final Set<BusEventListener> floatingSubscribers;

    // Lifecycle subscribers: the parent component is the weak key. Listeners live as long as the parent lives.
    private final java.util.Map<Object, java.util.List<BusEventListener>> lifecycleSubscribers;
    
    // Holds strong references for non-JComponent parents that don't support putClientProperty
    private final java.util.Map<Object, java.util.List<BusEventListener>> fallbackStrongReferences;

    public interface ExceptionHandler {
        void handle(Throwable throwable, BusEventListener listener, BusEvent event);
    }

    private static ExceptionHandler exceptionHandler = (throwable, receiver, event) -> {
        log.error("Error in listener {} handling event {}", getListenerName(receiver), event.getClass().getSimpleName(), throwable);
        Errors.printToStream(throwable);
    };

    public static void setExceptionHandler(ExceptionHandler handler) {
        exceptionHandler = handler;
    }

    public static ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    private static final ThreadLocal<DispatchContext> THREAD_DISPATCH_CONTEXT = ThreadLocal.withInitial(DispatchContext::new);

    private static class DispatchContext {
        final java.util.List<java.util.List<BusEventListener>> depthLists = new java.util.ArrayList<>();
        int currentDepth = 0;

        java.util.List<BusEventListener> getListForDepth() {
            if (currentDepth >= depthLists.size()) {
                depthLists.add(new java.util.ArrayList<>(128));
            }
            return depthLists.get(currentDepth);
        }
    }

    private EventBus() {
        this.floatingSubscribers = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
        this.lifecycleSubscribers = new java.util.WeakHashMap<>();
        this.fallbackStrongReferences = new java.util.WeakHashMap<>();
    }

    /**
     * Subscribes a listener that will be garbage collected if no other strong references to it exist.
     * WARNING: Do not pass anonymous lambdas here unless you save the reference!
     */
    @SuppressWarnings("UnusedReturnValue")
    public static BusEventListener subscribe(BusEventListener listener) {
        EVENT_BUS.floatingSubscribers.add(listener);
        return listener;
    }

    /**
     * Subscribes a listener whose lifecycle is bound to the provided parent object.
     * The listener will be kept alive as long as the parent is not garbage collected.
     */
    public static BusEventListener subscribe(Object lifecycleParent, BusEventListener listener) {
        if (lifecycleParent instanceof javax.swing.JComponent comp) {
            synchronized (comp) {
                @SuppressWarnings("unchecked")
                java.util.List<BusEventListener> keptAlive = (java.util.List<BusEventListener>) comp.getClientProperty("EventBus.listeners");
                if (keptAlive == null) {
                    keptAlive = new java.util.ArrayList<>();
                    comp.putClientProperty("EventBus.listeners", keptAlive);
                }
                keptAlive.add(listener);
            }
        } else {
            synchronized (EVENT_BUS.fallbackStrongReferences) {
                EVENT_BUS.fallbackStrongReferences.computeIfAbsent(lifecycleParent, k -> new java.util.ArrayList<>()).add(listener);
            }
        }

        synchronized (EVENT_BUS.lifecycleSubscribers) {
            EVENT_BUS.lifecycleSubscribers.computeIfAbsent(lifecycleParent, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(new WeakListenerWrapper(listener));
        }
        return listener;
    }

    /**
     * Subscribes a typed consumer whose lifecycle is bound to the provided parent object.
     * The consumer is only invoked when an event of the specified class (or subclass) is published.
     *
     * @param lifecycleParent the lifecycle parent object
     * @param eventType       the class of event to filter on
     * @param handler         the consumer callback
     * @param <T>             the event type
     * @return the registered listener handle
     */
    public static <T extends BusEvent> BusEventListener subscribe(Object lifecycleParent,
                                                                   Class<T> eventType,
                                                                   Consumer<T> handler) {
        return subscribe(lifecycleParent, event -> {
            if (eventType.isInstance(event)) {
                handler.accept(eventType.cast(event));
            }
        });
    }

    public static void unsubscribe(BusEventListener listener) {
        EVENT_BUS.floatingSubscribers.remove(listener);
        synchronized (EVENT_BUS.lifecycleSubscribers) {
            for (java.util.List<BusEventListener> list : EVENT_BUS.lifecycleSubscribers.values()) {
                list.removeIf(l -> l == listener || (l instanceof WeakListenerWrapper && ((WeakListenerWrapper) l).get() == listener));
            }
        }
        synchronized (EVENT_BUS.fallbackStrongReferences) {
            for (java.util.List<BusEventListener> list : EVENT_BUS.fallbackStrongReferences.values()) {
                list.remove(listener);
            }
        }
    }

    /**
     * Unsubscribes all listeners bound to the given parent object in O(1) time.
     */
    public static void unsubscribeByParent(Object lifecycleParent) {
        if (lifecycleParent instanceof javax.swing.JComponent comp) {
            synchronized (comp) {
                comp.putClientProperty("EventBus.listeners", null);
            }
        }
        synchronized (EVENT_BUS.lifecycleSubscribers) {
            EVENT_BUS.lifecycleSubscribers.remove(lifecycleParent);
        }
        synchronized (EVENT_BUS.fallbackStrongReferences) {
            EVENT_BUS.fallbackStrongReferences.remove(lifecycleParent);
        }
    }

    static volatile boolean TRACE_ENABLED = false;

    public static void publish(BusEvent event) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(() -> publish(event));
            return;
        }

        if (TRACE_ENABLED && log.isTraceEnabled()) {
            log.trace("Publishing event: {}", event.getClass().getSimpleName());
        }
        
        DispatchContext context = THREAD_DISPATCH_CONTEXT.get();
        java.util.List<BusEventListener> activeListeners = context.getListForDepth();
        context.currentDepth++;
        
        try {
            activeListeners.clear();
            
            synchronized (EVENT_BUS.floatingSubscribers) {
                activeListeners.addAll(EVENT_BUS.floatingSubscribers);
            }
            
            synchronized (EVENT_BUS.lifecycleSubscribers) {
                for (java.util.List<BusEventListener> list : EVENT_BUS.lifecycleSubscribers.values()) {
                    activeListeners.addAll(list);
                }
            }

            for (BusEventListener receiver : activeListeners) {
                if (receiver == null) continue;
                try {
                    receiver.handleEvent(event);
                } catch (Throwable throwable) {
                    if (exceptionHandler != null) {
                        exceptionHandler.handle(throwable, receiver, event);
                    }
                }
            }
        } finally {
            activeListeners.clear();
            context.currentDepth--;
        }
    }

    private static String getListenerName(BusEventListener listener) {
        Class<? extends BusEventListener> identity = listener.getClass();
        String shortName = identity.getSimpleName();
        String pattern = "/0x.*";
        // Apply the pattern and trim the string.
        return shortName.replaceAll(pattern, "");
    }

    private static class WeakListenerWrapper implements BusEventListener {
        final java.lang.ref.WeakReference<BusEventListener> ref;
        
        WeakListenerWrapper(BusEventListener listener) {
            this.ref = new java.lang.ref.WeakReference<>(listener);
        }
        
        @Override
        public void handleEvent(BusEvent event) {
            BusEventListener listener = ref.get();
            if (listener != null) {
                listener.handleEvent(event);
            }
        }
        
        public BusEventListener get() { 
            return ref.get(); 
        }
    }

    public static void reset() {
        synchronized (EVENT_BUS.floatingSubscribers) {
            EVENT_BUS.floatingSubscribers.clear();
        }
        synchronized (EVENT_BUS.lifecycleSubscribers) {
            EVENT_BUS.lifecycleSubscribers.clear();
        }
        synchronized (EVENT_BUS.fallbackStrongReferences) {
            EVENT_BUS.fallbackStrongReferences.clear();
        }
        TRACE_ENABLED = false;
        exceptionHandler = (throwable, receiver, event) -> {
            log.error("Error in listener {} handling event {}", getListenerName(receiver), event.getClass().getSimpleName(), throwable);
            Errors.printToStream(throwable);
        };
    }

}
