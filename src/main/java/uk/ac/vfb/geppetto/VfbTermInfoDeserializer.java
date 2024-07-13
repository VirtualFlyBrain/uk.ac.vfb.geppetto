package uk.ac.vfb.geppetto;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.List;
import com.google.gson.reflect.TypeToken;

public class VfbTermInfoDeserializer implements JsonDeserializer<VFBProcessTermInfoCachedJson.vfb_terminfo> {

    @Override
    public VFBProcessTermInfoCachedJson.vfb_terminfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        VFBProcessTermInfoCachedJson.vfb_terminfo vfbTermInfo = new VFBProcessTermInfoCachedJson().new vfb_terminfo();
        Gson gson = new Gson();

        try {
            JsonObject jsonObject = json.getAsJsonObject();

            try {
                vfbTermInfo.term = context.deserialize(jsonObject.get("term"), VFBProcessTermInfoCachedJson.term.class);
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'term' in JSON element: " + jsonObject.get("term") + " | Error: " + e.getMessage());
            }

            try {
                vfbTermInfo.query = jsonObject.get("query").getAsString();
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'query' in JSON element: " + jsonObject.get("query") + " | Error: " + e.getMessage());
            }

            try {
                vfbTermInfo.version = jsonObject.get("version").getAsString();
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'version' in JSON element: " + jsonObject.get("version") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("anatomy_channel_image")) {
                    vfbTermInfo.anatomy_channel_image = context.deserialize(jsonObject.get("anatomy_channel_image"), new TypeToken<List<VFBProcessTermInfoCachedJson.anatomy_channel_image>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'anatomy_channel_image' in JSON element: " + jsonObject.get("anatomy_channel_image") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("xrefs")) {
                    vfbTermInfo.xrefs = context.deserialize(jsonObject.get("xrefs"), new TypeToken<List<VFBProcessTermInfoCachedJson.xref>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'xrefs' in JSON element: " + jsonObject.get("xrefs") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("pub_syn")) {
                    vfbTermInfo.pub_syn = context.deserialize(jsonObject.get("pub_syn"), new TypeToken<List<VFBProcessTermInfoCachedJson.pub_syn>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'pub_syn' in JSON element: " + jsonObject.get("pub_syn") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("def_pubs")) {
                    vfbTermInfo.def_pubs = context.deserialize(jsonObject.get("def_pubs"), new TypeToken<List<VFBProcessTermInfoCachedJson.pub>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'def_pubs' in JSON element: " + jsonObject.get("def_pubs") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("pubs")) {
                    vfbTermInfo.pubs = context.deserialize(jsonObject.get("pubs"), new TypeToken<List<VFBProcessTermInfoCachedJson.pub>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'pubs' in JSON element: " + jsonObject.get("pubs") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("license")) {
                    vfbTermInfo.license = context.deserialize(jsonObject.get("license"), new TypeToken<List<VFBProcessTermInfoCachedJson.license>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'license' in JSON element: " + jsonObject.get("license") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("dataset_license")) {
                    vfbTermInfo.dataset_license = context.deserialize(jsonObject.get("dataset_license"), new TypeToken<List<VFBProcessTermInfoCachedJson.dataset_license>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'dataset_license' in JSON element: " + jsonObject.get("dataset_license") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("relationships")) {
                    vfbTermInfo.relationships = context.deserialize(jsonObject.get("relationships"), new TypeToken<List<VFBProcessTermInfoCachedJson.rel>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'relationships' in JSON element: " + jsonObject.get("relationships") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("related_individuals")) {
                    vfbTermInfo.related_individuals = context.deserialize(jsonObject.get("related_individuals"), new TypeToken<List<VFBProcessTermInfoCachedJson.rel>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'related_individuals' in JSON element: " + jsonObject.get("related_individuals") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("parents")) {
                    vfbTermInfo.parents = context.deserialize(jsonObject.get("parents"), new TypeToken<List<VFBProcessTermInfoCachedJson.minimal_entity_info>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'parents' in JSON element: " + jsonObject.get("parents") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("channel_image")) {
                    vfbTermInfo.channel_image = context.deserialize(jsonObject.get("channel_image"), new TypeToken<List<VFBProcessTermInfoCachedJson.channel_image>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'channel_image' in JSON element: " + jsonObject.get("channel_image") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("template_domains")) {
                    vfbTermInfo.template_domains = context.deserialize(jsonObject.get("template_domains"), new TypeToken<List<VFBProcessTermInfoCachedJson.domain>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'template_domains' in JSON element: " + jsonObject.get("template_domains") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("template_channel")) {
                    vfbTermInfo.template_channel = context.deserialize(jsonObject.get("template_channel"), VFBProcessTermInfoCachedJson.template_channel.class);
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'template_channel' in JSON element: " + jsonObject.get("template_channel") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("targeting_splits")) {
                    vfbTermInfo.targeting_splits = context.deserialize(jsonObject.get("targeting_splits"), new TypeToken<List<VFBProcessTermInfoCachedJson.minimal_entity_info>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'targeting_splits' in JSON element: " + jsonObject.get("targeting_splits") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("target_neurons")) {
                    vfbTermInfo.target_neurons = context.deserialize(jsonObject.get("target_neurons"), new TypeToken<List<VFBProcessTermInfoCachedJson.minimal_entity_info>>(){}.getType());
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'target_neurons' in JSON element: " + jsonObject.get("target_neurons") + " | Error: " + e.getMessage());
            }

            try {
                if (jsonObject.has("pub_specific_content")) {
                    vfbTermInfo.pub_specific_content = context.deserialize(jsonObject.get("pub_specific_content"), VFBProcessTermInfoCachedJson.pub_specific_content.class);
                }
            } catch (JsonParseException e) {
                System.out.println("Error parsing 'pub_specific_content' in JSON element: " + jsonObject.get("pub_specific_content") + " | Error: " + e.getMessage());
            }

        } catch (JsonParseException e) {
            System.out.println("General error parsing JSON element: " + json + " | Error: " + e.getMessage());
        }

        return vfbTermInfo;
    }
}
