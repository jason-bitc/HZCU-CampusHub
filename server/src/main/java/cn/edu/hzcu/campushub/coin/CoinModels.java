package cn.edu.hzcu.campushub.coin;

import java.time.*;
import java.util.List;

public final class CoinModels {
    private CoinModels(){}
    public enum Kind { LOGIN_REWARD,READING_REWARD,ARTICLE_COIN }
    public record Wallet(long balance,long revision){}
    public record Balance(long balance,long receivedContribution){}
    public record Rules(String version,String timezone,int loginReward,int readingReward,int readingSeconds,int articleLimit,int dailyMaximum,boolean readerCanWithdraw){}
    public record Reading(String sessionId,long articleId,LocalDate businessDate,Instant startedAt,Instant claimableAt,int requiredSeconds){}
    public record Receipt(String eventId,Kind action,LocalDate businessDate,Long articleId,String sessionId,long delta,long balanceAfter,long walletRevision,Instant occurredAt,Rules rules){}
    public record Ledger(List<Receipt> items,long total,int page,int size,long snapshot){}
    public record Task(boolean eligible,boolean claimed){}
    public record Rewards(LocalDate businessDate,Instant serverTime,Rules rules,Task login,Task reading,long rewardedToday,long remainingDailyReward,List<Reading> readingSessions){}
    public record CoinState(long articleId,long spent,int remaining,boolean eligible,String reason){}
    record Target(Long author,boolean visible){}
    record Event(String requestId,Receipt receipt){}
    public static Rules rules(CoinRules r){return new Rules(r.version(),CoinRules.ZONE.getId(),r.loginReward(),r.readingReward(),r.readingSeconds(),r.articleLimit(),r.dailyMaximum(),false);}
}
