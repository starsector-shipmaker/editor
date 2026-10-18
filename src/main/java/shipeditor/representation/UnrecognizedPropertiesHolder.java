package shipeditor.representation;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared base for spec files that must preserve unrecognized (custom third-party mod) JSON properties
 * non-destructively across a load/save round trip.
 */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public abstract class UnrecognizedPropertiesHolder {

    @JsonIgnore
    private final Map<String, Object> unrecognizedProperties = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getUnrecognizedProperties() {
        return unrecognizedProperties;
    }

    @JsonAnySetter
    public void setUnrecognizedProperty(String name, Object value) {
        unrecognizedProperties.put(name, value);
    }

}
