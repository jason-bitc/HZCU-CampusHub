package cn.edu.hzcu.campushub.coin;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record CoinRules(int loginReward,int readingReward,int readingSeconds,int articleLimit) {
    public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public CoinRules(@Value("${campushub.coins.login-reward:2}") int loginReward,
            @Value("${campushub.coins.reading-reward:1}") int readingReward,
            @Value("${campushub.coins.reading-seconds:180}") int readingSeconds,
            @Value("${campushub.coins.article-limit:2}") int articleLimit) {
        if(loginReward<1||loginReward>1000||readingReward<1||readingReward>1000||readingSeconds<1||readingSeconds>86400||articleLimit<1||articleLimit>1000)
            throw new IllegalArgumentException("Invalid coin rules");
        this.loginReward=loginReward;this.readingReward=readingReward;this.readingSeconds=readingSeconds;this.articleLimit=articleLimit;
    }
    public int dailyMaximum(){return loginReward+readingReward;}
    public String version(){return "v1-"+loginReward+"-"+readingReward+"-"+readingSeconds+"-"+articleLimit;}
}
