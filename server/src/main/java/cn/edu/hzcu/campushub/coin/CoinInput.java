package cn.edu.hzcu.campushub.coin;

import cn.edu.hzcu.campushub.authoring.WritingInput;
import cn.edu.hzcu.campushub.common.ApiException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

public final class CoinInput {
    private CoinInput(){}
    private static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public static JsonNode parse(String raw,String... fields){
        try{var n=JSON.readTree(raw);WritingInput.fields(n,fields);return n;}
        catch(RuntimeException failure){throw ApiException.invalid();}
    }
    public static String key(JsonNode n){return WritingInput.requestId(n);}
    public static String session(JsonNode n){return WritingInput.requestId(WritingInput.text(n,"sessionId",36));}
    public static int amount(JsonNode n){long amount=WritingInput.number(n.path("amount"));if(amount<1||amount>1000)throw ApiException.invalid();return (int)amount;}
}
