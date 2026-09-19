package jagent.agent;

import jagent.llm.Router;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Auction {

    public record Bid(Router.Tier tier, double p, double cost, double utility) {}

    public record Award(Router.Tier tier, double utility, double pay, double profit, List<Bid> bids) {}

    private static final double[] BASE_P = {0.70, 0.95};
    private static final double[] RATE = {0.15, 0.30};

    private final Map<Router.Tier, Double> rep = new EnumMap<>(Router.Tier.class);
    private final Map<Router.Tier, Integer> wins = new EnumMap<>(Router.Tier.class);
    private double profitTotal;

    public Auction() {
        rep.put(Router.Tier.CHEAP, 1.0);
        rep.put(Router.Tier.STRONG, 1.0);
        wins.put(Router.Tier.CHEAP, 0);
        wins.put(Router.Tier.STRONG, 0);
    }

    public double reputation(Router.Tier t) {
        return rep.getOrDefault(t, 1.0);
    }

    public int wins(Router.Tier t) { return wins.getOrDefault(t, 0); }

    public double profitTotal() { return profitTotal; }

    public Award award(double value, double kTokens) {
        List<Bid> bids = new ArrayList<>(2);
        for (Router.Tier t : Router.Tier.values()) {
            double p = BASE_P[t.ordinal()] * rep.get(t);
            double cost = RATE[t.ordinal()] * kTokens;
            bids.add(new Bid(t, p, cost, value * p - cost));
        }
        Bid best = bids.get(0);
        for (Bid b : bids) if (b.utility() > best.utility()) best = b;
        double second = Double.NEGATIVE_INFINITY;
        for (Bid b : bids) if (b != best && b.utility() > second) second = b.utility();
        double pay = second == Double.NEGATIVE_INFINITY ? best.utility() : second;
        double profit = pay - best.cost();
        profitTotal += profit;
        wins.merge(best.tier(), 1, Integer::sum);
        return new Award(best.tier(), best.utility(), pay, profit, bids);
    }

    public void settle(Router.Tier t, boolean ok) {
        double r = rep.getOrDefault(t, 1.0);
        r = 0.75 * r + 0.25 * (ok ? 1.0 : 0.0);
        rep.put(t, Math.max(0.2, Math.min(1.2, r)));
    }
}
