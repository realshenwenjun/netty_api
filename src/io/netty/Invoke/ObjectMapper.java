package io.netty.Invoke;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParser.Feature;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.SerializationConfig;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObjectMapper {
    private static org.codehaus.jackson.map.ObjectMapper objectMapper;

    static {
        objectMapper = new org.codehaus.jackson.map.ObjectMapper();
        objectMapper.setVisibility(JsonMethod.FIELD, Visibility.ANY);
        objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        objectMapper.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        objectMapper.configure(org.codehaus.jackson.map.DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Object转成String
     *
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     */
    public static String objToString(Object object) throws JsonGenerationException, JsonMappingException, IOException {
        // 将结果转化为JSON字符串
        String jsonString = objectMapper.writeValueAsString(object);
        Pattern p = Pattern.compile("\t|\r|\n");
        Matcher m = p.matcher(jsonString);
        jsonString = m.replaceAll("");
//		jsonString = jsonString.replaceAll(" ", "");
        return jsonString;
    }
}
