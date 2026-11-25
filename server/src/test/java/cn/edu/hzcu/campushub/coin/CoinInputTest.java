package cn.edu.hzcu.campushub.coin;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import cn.edu.hzcu.campushub.common.*;
import java.util.UUID;

class CoinInputTest {
    @Test void projectConfigurationEnablesAllFourConfirmedHonorThresholds() throws Exception {
        var configuration = new YamlPropertySourceLoader().load("project",new ClassPathResource("application.yml"));
        new ApplicationContextRunner().withBean(HonorRules.class)
                .withInitializer(context -> configuration.forEach(context.getEnvironment().getPropertySources()::addLast))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var rules = context.getBean(HonorRules.class);
                    assertThat(rules.current(4)).isNull();
                    assertThat(rules.current(5)).isEqualTo(new HonorRules.Honor("experience-sharer","经验分享者","sprout"));
                    assertThat(rules.current(20)).isEqualTo(new HonorRules.Honor("helpful-peer","热心同行者","open-book"));
                    assertThat(rules.current(50)).isEqualTo(new HonorRules.Honor("campus-supporter","校园助力者","star-book"));
                    assertThat(rules.current(99).ruleId()).isEqualTo("campus-supporter");
                    assertThat(rules.current(100)).isEqualTo(new HonorRules.Honor("campus-star","城院之星","lighthouse"));
                });
    }

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
