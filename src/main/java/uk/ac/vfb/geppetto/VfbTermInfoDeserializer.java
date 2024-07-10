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

            // Add more fields as needed

        } catch (JsonParseException e) {
            LOGGER.severe("Error parsing JSON element: " + json + " | Error: " + e.getMessage());
        }

        return vfbTermInfo;
    }
}
