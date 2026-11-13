package cn.edu.hzcu.campushub.guidance;

import cn.edu.hzcu.campushub.auth.AuthInput;
import cn.edu.hzcu.campushub.coin.CoinModels;
import cn.edu.hzcu.campushub.coin.CoinRules;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserGuidanceController {
    public record Reading(boolean free, String description) {}
    public record Certification(String emailDomain, String method, String description) {}
    public record Contact(boolean voluntary, boolean external, String description) {}
    public record Coins(String valueType, CoinModels.Rules rules, String description) {}
    public record Hint(String message, String nextAction) {}
    public record Rules(Reading reading, Certification certification, Contact contact, Coins coins,
                        Map<String, Hint> hints) {}
    public record WritingPrompt(String id, String title, String question) {}
    public record WritingPrompts(boolean optional, List<WritingPrompt> items) {}

    private static final Map<String, Hint> HINTS = Map.ofEntries(
            Map.entry("NO_ARTICLE_RESULTS", new Hint("没有找到符合当前条件的文章", "ADJUST_FILTERS")),
            Map.entry("NETWORK_ERROR", new Hint("暂时无法连接，请检查网络后重试", "RETRY")),
            Map.entry("UNAUTHORIZED", new Hint("登录后可继续操作", "LOGIN")),
            Map.entry("ACCESS_RESTRICTED", new Hint("请检查本人账号和校园认证状态", "CHECK_ACCOUNT")),
            Map.entry("BEHAVIOR_RESTRICTED", new Hint("该操作受限，可查看本人处理记录", "VIEW_RESTRICTIONS")),
            Map.entry("NOT_FOUND", new Hint("相关内容暂不可查看", "RETURN_TO_LIST")),
            Map.entry("SERVICE_UNAVAILABLE", new Hint("服务暂时不可用，请保留输入并稍后重试", "RETRY")),
            Map.entry("STORAGE_UNAVAILABLE", new Hint("图片存储暂时不可用，请保留输入并稍后重试", "RETRY")),
            Map.entry("MAIL_UNAVAILABLE", new Hint("邮件暂未发出，请稍后重新申请验证码", "RETRY")),
            Map.entry("LOGIN_REQUIRED_TODAY", new Hint("完成今日登录后可领取奖励", "LOGIN")),
            Map.entry("READING_NOT_READY", new Hint("继续阅读，达到任务时长后可领取", "CONTINUE_READING")),
            Map.entry("REWARD_ALREADY_CLAIMED", new Hint("今日这项奖励已领取", "VIEW_REWARDS")),
            Map.entry("INSUFFICIENT_COINS", new Hint("当前可用币不足，可查看奖励任务", "VIEW_REWARDS")),
            Map.entry("ARTICLE_COIN_LIMIT", new Hint("这篇文章已达到单人投币上限或本次投币超过剩余额度", "VIEW_COIN_STATE")));

    private static final WritingPrompts WRITING_PROMPTS = new WritingPrompts(true, List.of(
            new WritingPrompt("background", "当时的基础", "开始准备时，你学过什么，做过哪些项目？"),
            new WritingPrompt("preparation", "准备过程", "你按什么顺序准备，哪一步对你最有帮助？"),
            new WritingPrompt("applications", "投递与面试", "你如何选择岗位和投递渠道，面试经历了哪些环节？"),
            new WritingPrompt("reflection", "结果与复盘", "结果如何，哪些做法值得保留，下次会怎么调整？"),
            new WritingPrompt("sources", "资料来源", "哪些资料帮到了你，读者可以从哪里找到原出处？")));

    private final Rules rules;

    public UserGuidanceController(CoinRules coinRules) {
        rules = new Rules(new Reading(true, "公开文章可免费阅读全文"),
                new Certification(AuthInput.CAMPUS_EMAIL_DOMAIN, "CAMPUS_EMAIL_CODE", "注册时验证本人校园邮箱"),
                new Contact(true, true, "认证读者可自愿使用作者开放的联系方式，双方在站外自行交流"),
                new Coins("NON_CASH", CoinModels.rules(coinRules), "币用于认可经验分享，属于非现金激励"),
                HINTS);
    }

    @GetMapping("/rules")
    public ApiResponse<Rules> rules(HttpServletRequest request) throws IOException {
        requireEmptyRequest(request);
        return ApiResponse.success(rules);
    }

    @GetMapping("/me/writing-prompts")
    public ApiResponse<WritingPrompts> writingPrompts(HttpServletRequest request) throws IOException {
        requireEmptyRequest(request);
        return ApiResponse.success(WRITING_PROMPTS);
    }

    private static void requireEmptyRequest(HttpServletRequest request) throws IOException {
        if (request.getQueryString() != null || request.getContentLengthLong() > 0
                || request.getInputStream().read() != -1) {
            throw ApiException.invalid();
        }
    }
}
