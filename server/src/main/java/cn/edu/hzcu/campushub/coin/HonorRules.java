package cn.edu.hzcu.campushub.coin;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(HonorRules.Settings.class)
public class HonorRules {
    @ConfigurationProperties("campushub.honors")
    public record Settings(List<Rule> rules) {
        public Settings {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    public record Rule(String id, String title, long threshold, String badge, boolean enabled) {}
    public record Honor(String ruleId, String title, String badge) {}

    private final List<Rule> enabled;

    public HonorRules(Settings settings) {
        var ids = new HashSet<String>();
        var thresholds = new HashSet<Long>();
        for (Rule rule : settings.rules()) {
            if (rule.id() == null || !rule.id().matches("[a-z0-9][a-z0-9-]{0,63}")
                    || rule.title() == null || rule.title().isBlank() || rule.title().length() > 64
                    || rule.threshold() <= 0 || !ids.add(rule.id()) || !thresholds.add(rule.threshold())
                    || (rule.badge() != null && !rule.badge().matches("[a-z0-9][a-z0-9-]{0,63}"))) {
                throw new IllegalArgumentException("Invalid honor rule");
            }
        }
        enabled = settings.rules().stream()
                .filter(Rule::enabled)
                .sorted(Comparator.comparingLong(Rule::threshold).reversed())
                .toList();
    }

    public boolean hasEnabledRules() {
        return !enabled.isEmpty();
    }

    public Honor current(long receivedContribution) {
        for (Rule rule : enabled) {
            if (receivedContribution >= rule.threshold()) {
                return new Honor(rule.id(), rule.title(), rule.badge());
            }
        }
        return null;
    }
}
