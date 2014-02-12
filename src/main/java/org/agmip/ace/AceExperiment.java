package org.agmip.ace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.agmip.ace.util.AceFunctions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.hash.HashCode;

public class AceExperiment extends AceComponent implements IAceBaseComponent {
    private String eid;
    private AceWeather weather;
    private AceSoil    soil;
    private AceInitialConditions ic;
    private AceObservedData observed;
    private AceEventCollection events;

    public AceExperiment() throws IOException {
        this(AceFunctions.getBlankComponent());
    }

    public AceExperiment(byte[] source) throws IOException {
        super(source);
        this.extractSubcomponents();
        this.eid = this.getValue("eid");
        this.componentType = AceComponentType.ACE_EXPERIMENT;
        if(this.eid == null) {
            this.getId(true);
        }
    }

    public String getId(boolean forceRegenerate) throws IOException {
        if (forceRegenerate || this.eid == null) {
            this.eid = this.generateId();
            this.update("eid", this.eid, true);
        }
        return this.eid;
    }

    public boolean validId() throws IOException {
        if (this.eid == null) return false;
        return this.eid.equals(this.generateId());
    }

    // Should this be in the public scope? Why shouldn't a developer be
    // able to trigger this method?
    public String generateId() throws IOException {
        HashCode currentHash = this.getRawComponentHash();
        currentHash = AceFunctions.generateHCId(currentHash.asBytes(), this.ic.getRawComponentHash().asBytes());
        for(AceRecord r: this.ic.getSoilLayers()) {
            currentHash = AceFunctions.generateHCId(currentHash.asBytes(), r.getRawComponentHash().asBytes());
        }
        for(AceEvent e: this.events.asList()) {
            currentHash = AceFunctions.generateHCId(currentHash.asBytes(), e.getRawComponentHash().asBytes());
        }
        currentHash = AceFunctions.generateHCId(currentHash.asBytes(), this.observed.getRawComponentHash().asBytes());
        for(AceRecord r: this.observed.getTimeseries()) {
            currentHash = AceFunctions.generateHCId(currentHash.asBytes(), r.getRawComponentHash().asBytes());
        }
        return currentHash.toString();
    }

    public String getId() throws IOException {
        return getId(false);
    }

    public AceComponentType getComponentType() {
        return this.componentType;
    }

    public AceWeather getWeather() {
        return this.weather;
    }

    public AceSoil getSoil() {
        return this.soil;
    }

    public void setWeather(AceWeather weather) {
        this.weather = weather;
    }

    public void setSoil(AceSoil soil) {
        this.soil = soil;
    }

    public AceInitialConditions getInitialConditions() throws IOException {
        return this.ic;
    }

    public AceObservedData getOberservedData() {
        return this.observed;
    }

    public AceEventCollection getEvents() {
        return this.events;
    }

    private void extractSubcomponents() throws IOException {
        ByteArrayOutputStream baseOut = new ByteArrayOutputStream();
        JsonParser p = this.getParser();
        JsonGenerator g = this.getGenerator(baseOut);
        JsonToken t;

        t = p.nextToken();
        boolean inManagement = false;
        while (t != null) {
            String currentName = p.getCurrentName();
            if (currentName != null && t == JsonToken.FIELD_NAME && currentName.equals("management")) {
                inManagement = true;
            }
            if(currentName != null && t == JsonToken.FIELD_NAME &&
                    (currentName.equals("initial_conditions") ||
                     currentName.equals("observed") ||
                     currentName.equals("events"))) {
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                t = p.nextToken();
                JsonGenerator subcomponent = this.getGenerator(outStream);
                subcomponent.copyCurrentStructure(p);
                subcomponent.flush();
                byte[] out = outStream.toByteArray();
                subcomponent.close();
                if (currentName.equals("initial_conditions")) {
                    this.ic = new AceInitialConditions(out);
                } else if (currentName.equals("observed")) {
                    this.observed = new AceObservedData(out);
                } else if (currentName.equals("events")) {
                    this.events = new AceEventCollection(out);
                }
            } else {
                if (!inManagement) {
                    g.copyCurrentEvent(p);
                }
            }
            if(inManagement && t == JsonToken.END_OBJECT) {
                inManagement = false;
            }
            t = p.nextToken();
        }
        if (this.ic == null) {
            this.ic = new AceInitialConditions(AceFunctions.getBlankComponent());
        }
        if (this.observed == null) {
            this.observed = new AceObservedData(AceFunctions.getBlankComponent());
        }
        if (this.events == null) {
            this.events = new AceEventCollection(AceFunctions.getBlankSeries());
        }
        p.close();
        g.flush();
        g.close();
        this.component = baseOut.toByteArray();
    }

    public byte[] rebuildComponent() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonParser p = this.getParser();
        JsonGenerator g = this.getGenerator(bos);
        JsonToken t = p.nextToken();
        int level = 0;
        while ( t != null) {
            if (t == JsonToken.START_OBJECT) {
                level++;
            } else  if (t == JsonToken.END_OBJECT) {
                level--;
            }
            if (t == JsonToken.END_OBJECT && level == 0) {
                // Write the initial conditions
                g.writeFieldName("initial_conditions");
                JsonParser subP = this.getInitialConditions().getParser();
                JsonToken  subT = subP.nextToken();
                while (subT != null) {
                    if (subT == JsonToken.END_OBJECT) {
                        g.writeArrayFieldStart("soilLayer");
                        for (AceRecord r : this.getInitialConditions().getSoilLayers()) {
                            g.writeRawValue(new String(r.getRawComponent(), "UTF-8"));
                        }
                        g.writeEndArray();
                    }
                    g.copyCurrentEvent(subP);
                    subT = subP.nextToken();
                }
                subP.close();
                g.writeObjectFieldStart("management");
                g.writeArrayFieldStart("events");
                // Write the events
                for (AceEvent e: this.getEvents().asList()) {
                    g.writeRawValue(new String(e.getRawComponent(), "UTF-8"));
                }
                g.writeEndArray();
                g.writeEndObject();
                // Write the observed
                g.writeFieldName("observed");
                subP = this.getOberservedData().getParser();
                subT = subP.nextToken();

                while (subT != null) {
                    if (subT == JsonToken.END_OBJECT) {
                        g.writeArrayFieldStart("timeSeries");
                        for (AceRecord r: this.getOberservedData().getTimeseries()) {
                            g.writeRawValue(new String(r.getRawComponent(), "UTF-8"));
                        }
                        g.writeEndArray();
                    }
                    g.copyCurrentEvent(subP);
                    subT = subP.nextToken();
                }
                subP.close();
            }
            g.copyCurrentEvent(p);
            t = p.nextToken();
        }
        p.close();
        g.flush();
        g.close();
        bos.close();
        if (bos.size() == 0) {
            return AceFunctions.getBlankComponent();
        } else {
            return bos.toByteArray();
        }
    }

}

