package account.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class ResultPayloadMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ResultPayloadMapper() {
    }

    public static Result<Void> flatten(ObjectMapper objectMapper, Object data, String message) {
        if (data == null) {
            return Result.success(message);
        }
        Result<Void> result = Result.success(message);
        Map<String, Object> payload = objectMapper.convertValue(data, MAP_TYPE);
        payload.forEach(result::put);
        return result;
    }
}
