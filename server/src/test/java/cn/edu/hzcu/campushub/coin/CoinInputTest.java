package cn.edu.hzcu.campushub.coin;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import cn.edu.hzcu.campushub.common.*;
import java.util.UUID;

class CoinInputTest {
    @Test void defaultsAndEveryRuleHaveBoundedStartupValidation(){
        var runner=new ApplicationContextRunner().withBean(CoinRules.class);
        runner.run(ctx->{assertThat(ctx).hasNotFailed();var r=ctx.getBean(CoinRules.class);assertThat(r.dailyMaximum()).isEqualTo(3);assertThat(r.readingSeconds()).isEqualTo(180);assertThat(r.articleLimit()).isEqualTo(2);});
        for(String property:new String[]{"login-reward=0","reading-reward=-1","reading-seconds=86401","article-limit=1001"})runner.withPropertyValues("campushub.coins."+property).run(ctx->assertThat(ctx).hasFailed());
    }
    @Test void strictObjectsRejectDuplicateUnknownMissingAndTrailingFields(){
        String key=UUID.randomUUID().toString();
        for(String raw:new String[]{"{}","[]","null","{\"requestId\":\""+key+"\",\"userId\":1}","{\"requestId\":\""+key+"\",\"requestId\":\""+key+"\"}","{\"requestId\":\""+key+"\"} {}"})assertThatThrownBy(()->CoinInput.parse(raw,"requestId")).isInstanceOf(ApiException.class);
        assertThat(CoinInput.key(CoinInput.parse("{\"requestId\":\""+key+"\"}","requestId"))).isEqualTo(key);
    }
    @Test void coinAmountsRequirePositiveBoundedIntegersAndLowercaseKeys(){
        for(String value:new String[]{"0","-1","1.0","1.5","1001","9223372036854775808","\"1\"","true","null"})assertThatThrownBy(()->CoinInput.amount(CoinInput.parse("{\"amount\":"+value+"}","amount"))).isInstanceOf(ApiException.class);
        assertThat(CoinInput.amount(CoinInput.parse("{\"amount\":2}","amount"))).isEqualTo(2);
        assertThatThrownBy(()->CoinInput.key(CoinInput.parse("{\"requestId\":\"ABCDEFAB-ABCD-ABCD-ABCD-ABCDEFABCDEF\"}","requestId"))).isInstanceOf(ApiException.class);
    }
    @Test void everyCoinRouteHasFiniteLogActionAndModule(){
        for(String path:new String[]{"/me/coins","/me/coin-ledger","/me/rewards","/me/rewards/login","/me/rewards/reading","/articles/1/reading-session","/articles/1/coin-state","/articles/1/coins"}){String action=RequestTrace.action("/api/v1"+path,"POST");assertThat(cn.edu.hzcu.campushub.audit.SafeFileLogs.ACTIONS).contains(action);assertThat(RequestTrace.module(action)).isEqualTo("COIN");}
    }
}
