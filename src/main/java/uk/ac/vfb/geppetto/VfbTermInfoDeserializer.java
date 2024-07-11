package uk.ac.vfb.geppetto;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.List;
import java.util.logging.Logger;
import com.google.gson.reflect.TypeToken;

public class VfbTermInfoDeserializer implements JsonDeserializer<VFBProcessTermInfoCachedJson.vfb_terminfo> {

    private static final Logger LOGGER = Logger.getLogger(VfbTermInfoDeserializer.class.getName());

    @Override
    public VFBProcessTermInfoCachedJson.vfb_terminfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        VFBProcessTermInfoCachedJson.vfb_terminfo vfbTermInfo = new VFBProcessTermInfoCachedJson().new vfb_terminfo();
        Gson gson = new Gson();

        try {
            JsonObject jsonObject = json.getAsJsonObject();
            vfbTermInfo.term = context.deserialize(jsonObject.get("term"), VFBProcessTermInfoCachedJson.term.class);
            vfbTermInfo.query = jsonObject.get("query").getAsString();
            vfbTermInfo.version = jsonObject.get("version").getAsString();

            if (jsonObject.has("anatomy_channel_image")) {
                vfbTermInfo.anatomy_channel_image = context.deserialize(jsonObject.get("anatomy_channel_image"), new TypeToken<List<VFBProcessTermInfoCachedJson.anatomy_channel_image>>(){}.getType());
            }
            if (jsonObject.has("xrefs")) {
                vfbTermInfo.xrefs = context.deserialize(jsonObject.get("xrefs"), new TypeToken<List<VFBProcessTermInfoCachedJson.xref>>(){}.getType());
            }
            if (jsonObject.has("pub_syn")) {
                vfbTermInfo.pub_syn = context.deserialize(jsonObject.get("pub_syn"), new TypeToken<List<VFBProcessTermInfoCachedJson.pub_syn>>(){}.getType());
            }
            if (jsonObject.has("def_pubs")) {
                vfbTermInfo.def_pubs = context.deserialize(jsonObject.get("def_pubs"), new TypeToken<List<VFBProcessTermInfoCachedJson.pub>>(){}.getType());
            }
            if (jsonObject.has("pubs")) {
                vfbTermInfo.pubs = context.deserialize(jsonObject.get("pubs"), new TypeToken<List<VFBProcessTermInfoCachedJson.pub>>(){}.getType());
            }
            if (jsonObject.has("license")) {
                vfbTermInfo.license = context.deserialize(jsonObject.get("license"), new TypeToken<List<VFBProcessTermInfoCachedJson.license>>(){}.getType());
            }
            if (jsonObject.has("dataset_license")) {
                vfbTermInfo.dataset_license = context.deserialize(jsonObject.get("dataset_license"), new TypeToken<List<VFBProcessTermInfoCachedJson.dataset_license>>(){}.getType());
            }
            if (jsonObject.has("relationships")) {
                vfbTermInfo.relationships = context.deserialize(jsonObject.get("relationships"), new TypeToken<List<VFBProcessTermInfoCachedJson.rel>>(){}.getType());
            }
            if (jsonObject.has("related_individuals")) {
                vfbTermInfo.related_individuals = context.deserialize(jsonObject.get("related_individuals"), new TypeToken<List<VFBProcessTermInfoCachedJson.rel>>(){}.getType());
            }
            if (jsonObject.has("parents")) {
                vfbTermInfo.parents = context.deserialize(jsonObject.get("parents"), new TypeToken<List<VFBProcessTermInfoCachedJson.minimal_entity_info>>(){}.getType());
            }
            if (jsonObject.has("channel_image")) {
                vfbTermInfo.channel_image = context.deserialize(jsonObject.get("channel_image"), new TypeToken<List<VFBProcessTermInfoCachedJson.channel_image>>(){}.getType());
            }
            if (jsonObject.has("template_domains")) {
                vfbTermInfo.template_domains = context.deserialize(jsonObject.get("template_domains"), new TypeToken<List<VFBProcessTermInfoCachedJson.domain>>(){}.getType());
            }
            if (jsonObject.has("template_channel")) {
                vfbTermInfo.template_channel = context.deserialize(jsonObject.get("template_channel"), VFBProcessTermInfoCachedJson.template_channel.class);
            }
            if (jsonObject.has("targeting_splits")) {
                vfbTermInfo.targeting_splits = context.deserialize(jsonObject.get("targeting_splits"), new TypeToken<List<VFBProcessTermInfoCachedJson.minimal_entity_info>>(){}.getType());
            }
            if (jsonObject.has("target_neurons")) {
                vfbTermInfo.target_neurons = context.deserialize(jsonObject.get("target_neurons"), new TypeToken<List<VFBProcessTermInfoCachedJson.minimal_entity_info>>(){}.getType());
            }
            if (jsonObject.has("pub_specific_content")) {
                vfbTermInfo.pub_specific_content = context.deserialize(jsonObject.get("pub_specific_content"), VFBProcessTermInfoCachedJson.pub_specific_content.class);
            }

        } catch (JsonParseException e) {
            LOGGER.severe("Error parsing JSON element: " + json + " | Error: " + e.getMessage());
        }

        return vfbTermInfo;
    }
}
