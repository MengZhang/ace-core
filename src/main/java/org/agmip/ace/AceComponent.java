package org.agmip.ace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.agmip.ace.util.AceFunctions;
import org.agmip.ace.util.JsonFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.hash.HashCode;


/**
 * The base class for all components of a dataset.
 */
public class AceComponent {
    private static final Logger log = LoggerFactory
            .getLogger(AceComponent.class);
    protected byte[] component;
    protected boolean hasUpdate;
    
    /**
     * Return the type of component this object is.
     *
     * @see AceComponentType
     */
    public AceComponentType componentType;

    /**
     * Create a blank component.
     */
    public AceComponent() throws IOException {
        this.component = AceFunctions.getBlankComponent();
        this.hasUpdate = false;
    }

    /**
     * Create a new component based on JSON {@code byte[]}.
     */
    public AceComponent(byte[] component) throws IOException {
        this.component = component;
        this.hasUpdate = false;
    }
    
    /**
     * Return the raw {@code byte[]} for this component.
     *
     * @return raw {@code byte} for this component.
     */
    public byte[] getRawComponent() throws IOException {
        return this.component;
    }

    /**
     * Returns if this component has been updated.
     *
     * @return component update status.
     */
    public boolean isUpdated() {
        return this.hasUpdate;
    }


    /**
     * Provides a {@link JsonParser} for this component.
     *
     * @return a JsonParser for this component.
     */
    public JsonParser getParser() throws IOException {
        return JsonFactoryImpl.INSTANCE.getParser(this.component);
    }

    /**
     * Provides an empty {@link JsonGenerator}.
     * <p>
     * This method create an empty JsonGenerator linked to an
     * anonymous {@link ByteArrayOutputStream}.
     *
     * @return a JsonGenerator
     */
    public JsonGenerator getGenerator() throws IOException {
        return JsonFactoryImpl.INSTANCE
                .getGenerator(new ByteArrayOutputStream());
    }

    /**
     * Provides a new {@link JsonGenerator} for the given {@link OutputStream}.
     *
     * This method creates a new JsonGenerator link to a user
     * provided OutpuStream. The user must {@code close()} the 
     * OutputStream.
     */
    public JsonGenerator getGenerator(OutputStream stream) throws IOException {
        return JsonFactoryImpl.INSTANCE.getGenerator(stream);
    }

    /**
     * Return a value from the component, or return a default value.
     * <p>
     * <strong>NOTE:</strong>Use this for values only, not to retrieve
     * subcomponents. Use class specific methods to retrieve subcompnents.
     * <p>
     * Calls {@link #getValue} on the current component. If the value
     * is {@code null}, return {@code alternateValue}.
     *
     * @param key Key to look up in the component.
     * @param alternateValue default value is key is not found in component.
     * @return a String value for this component.
     */
    public String getValueOr(String key, String alternateValue)
            throws IOException {
        String value = this.getValue(key);
        if (value == null) {
            return alternateValue;
        } else {
            return value;
        }
    }

    /**
     * Return a value from the component.
     * <p>
     * <strong>NOTE:</strong>Use this for values only, not to retrieve
     * subcomponents. Use class specific methods to retrieve subcompnents.
     * <p>
     * Retrieve a value from the current component. If {@code key} is not
     * found, return {@code null}.
     *
     * @param key key to lookup in the component.
     * @return a String value for this component.
     */
    public String getValue(String key) throws IOException {
        JsonParser p = this.getParser();
        JsonToken t;

        t = p.nextToken();

        while (t != null) {
            if (t == JsonToken.FIELD_NAME && p.getCurrentName().equals(key)) {
                String value = p.nextTextValue();
                p.close();
                return value;
            }
            t = p.nextToken();
        }
        p.close();
        return null;
    }
    
    /**
     * Return the {@code HashCode} for this component.
     * <p>
     * Return the Google Guava <a href="http://docs.guava-libraries.googlecode.com/git-history/v14.0.1/javadoc/index.html">HashCode</a>
     * for this component.
     *
     * @return HashCode for this component.
     */
    public HashCode getRawComponentHash() throws IOException {
        return AceFunctions.generateHCId(this.component);
    }

    /**
     * Return the {@code byte[]} JSON array for the key in this component.
     * <p>
     * Like {@link #getValueOr} for records. This will grab the JSON array
     * for records (for example: {@code dailyRecords} in an
     * {@link AceWeather} component). If the key is not found or is not a JSON
     * array, a blank series is returned.
     *
     * @param key key for the JSON array for this component.
     * @return {@code byte[]} for JSON array or a blank series (@code {}).
     */
    public byte[] getRawRecords(String key) throws IOException {
        JsonParser p = this.getParser();
        JsonToken t;

        t = p.nextToken();

        while (t != null) {
            if (t == JsonToken.FIELD_NAME && p.getCurrentName().equals(key)) {
                t = p.nextToken();
                if (p.isExpectedStartArrayToken()) {
                    JsonGenerator g = this.getGenerator();
                    g.copyCurrentStructure(p);
                    g.flush();
                    byte[] subcomponent = ((ByteArrayOutputStream) g
                            .getOutputTarget()).toByteArray();
                    g.close();
                    p.close();
                    return subcomponent;
                } else {
                    log.error("Key {} does not start an array.", key);
                    return AceFunctions.getBlankSeries();
                }
            }
            t = p.nextToken();
        }
//        log.debug("Did not find key: {} in {}", key, new String(this.component, "UTF-8"));
        return AceFunctions.getBlankSeries();
    }

    /**
     * Return an {@link AceRecordCollection} for the key of this component.
     * <p>
     * <strong>NOTE:</strong> All base component types use this method to
     * return specific collections for their use. Please use those methods
     * instead.
     * <p>
     * Calls {@link #getRecords} and returns the AceRecordCollection for that
     * series.
     * 
     * @param key key for the JSON array for this component.
     * @return an AceRecordCollection for the JSON array.
     */
    public AceRecordCollection getRecords(String key) throws IOException {
        return new AceRecordCollection(this.getRawRecords(key));
    }


    /**
     * Return the {@code byte[]} JSON object for the key in this component.
     * 
     * Like {@link #getValueOr} for subcomponents. This will grab the JSON
     * object for a subcomponent (for example: {@code initial_condition} for
     * {@link AceExperiment}). If the key is not found or is not a JSON object, 
     * a blank subcomponent is returned.
     *
     * @param key key for a JSON object in this component.
     * @return {@code byte[]} for the JSON object or {@code {}}
     */
    public byte[] getRawSubcomponent(String key) throws IOException {
        JsonParser p = this.getParser();
        JsonToken t = p.nextToken();

        while (t != null) {
            if (t == JsonToken.FIELD_NAME && p.getCurrentName().equals(key)) {
                t = p.nextToken();
                if (t == JsonToken.START_OBJECT) {
                    JsonGenerator g = this.getGenerator();
                    g.copyCurrentStructure(p);
                    g.flush();
                    byte[] subcomponent = ((ByteArrayOutputStream) g
                            .getOutputTarget()).toByteArray();
                    g.close();
                    p.close();
                    return subcomponent;
                } else {
                    log.error("Key {} does not start an object.", key);
                    return AceFunctions.getBlankComponent();
                }
            }
            t = p.nextToken();
        }
//        log.debug("Did not find key: {} in {}", key, new String(this.component, "UTF-8"));
        return AceFunctions.getBlankComponent();
    }

    /**
     * Return an AceComponent for the key of this component.
     * <p>
     * <strong>NOTE:</strong> All base component types use this method to
     * return specific objects for their use. Please use those methods
     * instead.
     * <p>
     * Call {@link #getRawSubcomponent} and return the AceComponent for that
     * object.
     *
     * @param key key for the JSON object in this component.
     * @return an AceComponent for the JSON object.
     */
    public AceComponent getSubcomponent(String key) throws IOException {
        return new AceComponent(this.getRawSubcomponent(key));
    }

    /**
     * Returns the JSON string of this component.
     * <p>
     * <strong>NOTE:</strong> this will not contain subcomponents or
     * arrays that are being handled by other AceComponents.
     *
     * @return JSON string of this component.
     */
    public String toString() {
        return new String(this.component);
    }

  
    /**
     * Update a key-value entry in a component.
     * <p>
     * Update a key-value pair in this component, base on the following
     * conditions:
     * <p>
     * <ul>
     * <li> {@code removeMode} overrides all other arguments.
     * <li> if {@code key} does not belong in this component,
     * it will not be added.</li>
     * </ul>
     * <p>
     * This method can be chained.
     *
     * @param key key for the JSON value in this component.
     * @param newValue new value for the {@code key} in this component.
     * @param addIfMissing if {@code true} add this value to the component.
     * @param removeMode if {@code true} remove this value from the component.
     * @return {@code this} component after being updated.
     */
    public AceComponent update(String key, String newValue,
            boolean addIfMissing, boolean removeMode) throws IOException {
        AceComponentType updateComponentType = AceFunctions
                .getComponentTypeFromKey(key);
        if (updateComponentType == this.componentType || key.equals("wid")
                || key.equals("sid") || key.equals("eid")) {
            // The update can proceed.
            boolean updated = false;
            int nestedLevel = 0;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonParser p = this.getParser();
            JsonGenerator g = this.getGenerator(out);
            JsonToken t = p.nextToken();

            while (t != null) {
                if (t == JsonToken.START_OBJECT) {
                    nestedLevel++;
                }
                if (p.getCurrentName() != null && p.getCurrentName().equals(key)){
                    if (nestedLevel == 1 && t == JsonToken.FIELD_NAME) {
			if (!removeMode) {
			    g.writeStringField(key, newValue);
			}
			updated = true;
			this.hasUpdate = true;
			t = p.nextToken(); // Now on value_node
			t = p.nextToken(); // Now on next_valid_node?
		    }
                }
                if (t == JsonToken.END_OBJECT) {
                    if (nestedLevel == 1 && !updated && !removeMode
			&& addIfMissing) {
                        g.writeStringField(key, newValue);
			this.hasUpdate = true;
                    }
                    nestedLevel--;
                }
                g.copyCurrentEvent(p);
                t = p.nextToken();
            }
            g.flush();
            g.close();
            this.component = out.toByteArray();
        } else {
            log.error("Failed to update key: {}",key);
        }
        
        return this;
    }

    /**
     * An alias for {@code update(key, newValue, true, false)}.
     * <p>
     * Add or update a key with the given value.
     *
     * @param key key for the JSON value in this component.
     * @param newValue new value for the {@code key} in this component.
     * @return {@code this} component after being updated.
     */
    public AceComponent update(String key, String newValue) throws IOException {
	return this.update(key, newValue, true, false);
    }
    
    /**
     * An alias for {@code update(key, newValue, addIfMissing, false)}.
     *
     * @param key key for this JSON value in this component.
     * @param newValue new value for the {@code key} in this component.
     * @param addIfMissing if {@code true}, add this value to the component.
     * @return {@code this} component after being updated.
     */
    public AceComponent update(String key, String newValue, boolean addIfMissing)
            throws IOException {
        return this.update(key, newValue, addIfMissing, false);
    }

    /**
     * An alias for {@code update(key, "", false, true)}.
     *
     * @param key key to be removed from this component.
     * @return {@code this} component after being updated.
     */
    public AceComponent remove(String key) throws IOException {
        return this.update(key, "", false, true);
    }
}
