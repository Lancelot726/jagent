package jagent.agent;

import jagent.graph.Admission;
import jagent.graph.Graph;
import jagent.graph.NodeState;
import jagent.llm.FallbackClient;
import jagent.llm.Router;
import jagent.term.Lang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Scorecard {

    private final Auction auction;
    private final Budget budget;
    private final Governor gov;
    private final Admission adm;
    private final Graph graph;
    private final FallbackClient cheap;
    private final FallbackClient strong;
    private final Lang lang;

    private int compressCount;
    private long compressSaved;

    public Scorecard(Auction auction, Budget budget, Governor gov, Admission adm, Graph graph,
                     FallbackClient cheap, FallbackClient strong, Lang lang) {
        this.auction = auction;
        this.budget = budget;
        this.gov = gov;
        this.adm = adm;
        this.graph = graph;
        this.cheap = cheap;
        this.strong = strong;
        this.lang = lang;
    }

    public void onCompress(long before, long after) {
        compressCount++;
        compressSaved += Math.max(0, before - after);
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        int cw = auction.wins(Router.Tier.CHEAP);
        int sw = auction.wins(Router.Tier.STRONG);
        out.add(lang.t("score.title"));
        out.add(lang.t("score.auction", cw + sw, cw, sw, auction.profitTotal()));
        out.add(lang.t("score.rep",
                auction.reputation(Router.Tier.CHEAP), auction.reputation(Router.Tier.STRONG)));
        out.add(lang.t("score.budget", budget.spent(), budget.cap(),
                budget.usedFraction() * 100.0, budget.burnPerSec()));
        out.add(lang.t("score.compress", compressCount, compressSaved));
        out.add(lang.t("score.gov", adm.peak(), gov.permits(), gov.ups(), gov.downs(),
                gov.trips(), gov.errorRate() * 100.0));
        out.add(lang.t("score.path", adm.pathConflicts()));
        out.add(lang.t("score.fallback", cheap.fallbacks() + strong.fallbacks(),
                cheap.retries() + strong.retries()));
        out.add(lang.t("score.graph", graph.size(), graph.count(NodeState.DONE),
                graph.count(NodeState.FAILED), graph.snapshot().inTokens(), graph.snapshot().outTokens()));
        return out;
    }

    public String text() {
        return String.join(System.lineSeparator(), lines()) + System.lineSeparator();
    }

    public Path save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text(), StandardCharsets.UTF_8);
        return file;
    }
}
