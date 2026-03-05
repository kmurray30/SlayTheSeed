package seedsearch.seed_explorer.web_app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import io.javalin.Javalin;
import io.javalin.http.Context;
import seedsearch.core.SearchSettings;
import seedsearch.core.engine.ActMapSnapshot;
import seedsearch.core.engine.DecisionPoint;
import seedsearch.core.engine.RunEngine;
import seedsearch.core.engine.RunState;
import seedsearch.seed_explorer.InteractivePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API for the step-wise seed explorer web app.
 */
public class ApiController {

    private static final Gson gson = new GsonBuilder().create();
    private static final Map<String, RunEngine> runs = new ConcurrentHashMap<>();

    public static void registerRoutes(Javalin app) {
        app.post("/api/run/start", ApiController::startRun);
        app.post("/api/run/{runId}/choose", ApiController::applyChoice);
        app.get("/api/run/{runId}", ApiController::getRunState);
    }

    private static void startRun(Context ctx) {
        StartRequest req = gson.fromJson(ctx.body(), StartRequest.class);
        if (req == null || req.seed == null || req.playerClass == null) {
            ctx.status(400).json(Collections.singletonMap("error", "Missing seed or playerClass"));
            return;
        }

        AbstractPlayer.PlayerClass playerClass;
        try {
            playerClass = AbstractPlayer.PlayerClass.valueOf(req.playerClass);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Collections.singletonMap("error", "Invalid playerClass: " + req.playerClass));
            return;
        }

        int ascension = req.ascensionLevel != null ? req.ascensionLevel : 20;
        SearchSettings settings = createDefaultSettings(playerClass, ascension);
        seedsearch.seed_search.SeedSearch.setupUnlockTrackerForRun(settings);

        RunEngine engine = new RunEngine();
        engine.init(req.seed, playerClass, ascension, settings);

        String runId = UUID.randomUUID().toString();
        runs.put(runId, engine);

        DecisionPoint decision = engine.run(new InteractivePolicy());
        ActMapSnapshot actMap = engine.getActMap();
        RunState runState = buildRunState(engine);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runId", runId);
        response.put("decision", decision != null ? decision : Collections.emptyMap());
        response.put("actMap", actMap != null ? actMap : Collections.emptyMap());
        response.put("runState", runState);
        ctx.json(response);
    }

    private static void applyChoice(Context ctx) {
        String runId = ctx.pathParam("runId");
        RunEngine engine = runs.get(runId);
        if (engine == null) {
            ctx.status(404).json(Collections.singletonMap("error", "Run not found"));
            return;
        }

        ChooseRequest req = gson.fromJson(ctx.body(), ChooseRequest.class);
        if (req == null || req.choice == null) {
            ctx.status(400).json(Collections.singletonMap("error", "Missing choice"));
            return;
        }

        try {
            Object choice = parseChoice(req.choice, engine.getCurrentDecision());
            engine.applyChoice(choice);
        } catch (Exception e) {
            ctx.status(400).json(Collections.singletonMap("error", e.getMessage()));
            return;
        }

        DecisionPoint decision = engine.run(new InteractivePolicy());
        ActMapSnapshot actMap = engine.getActMap();
        RunState runState = buildRunState(engine);

        if (decision == null) {
            runs.remove(runId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("decision", decision != null ? decision : Collections.emptyMap());
        response.put("actMap", actMap != null ? actMap : Collections.emptyMap());
        response.put("runState", runState);
        response.put("complete", decision == null);
        ctx.json(response);
    }

    private static void getRunState(Context ctx) {
        String runId = ctx.pathParam("runId");
        RunEngine engine = runs.get(runId);
        if (engine == null) {
            ctx.status(404).json(Collections.singletonMap("error", "Run not found"));
            return;
        }

        DecisionPoint decision = engine.getCurrentDecision();
        ActMapSnapshot actMap = engine.getActMap();
        RunState runState = buildRunState(engine);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("decision", decision != null ? decision : Collections.emptyMap());
        response.put("actMap", actMap != null ? actMap : Collections.emptyMap());
        response.put("runState", runState);
        ctx.json(response);
    }

    private static Object parseChoice(Object choiceJson, DecisionPoint currentDecision) {
        if (currentDecision instanceof DecisionPoint.NeowChoice) {
            if (choiceJson instanceof Number) {
                return ((Number) choiceJson).intValue();
            }
            return Integer.parseInt(String.valueOf(choiceJson));
        }
        if (currentDecision instanceof DecisionPoint.BossRelicChoice) {
            return choiceJson == null ? "" : String.valueOf(choiceJson);
        }
        if (currentDecision instanceof DecisionPoint.PathChoice) {
            return String.valueOf(choiceJson);
        }
        if (currentDecision instanceof DecisionPoint.CardRewardChoice) {
            return choiceJson == null ? "" : String.valueOf(choiceJson);
        }
        return choiceJson;
    }

    private static RunState buildRunState(RunEngine engine) {
        return engine.getRunState();
    }

    private static SearchSettings createDefaultSettings(AbstractPlayer.PlayerClass playerClass, int ascension) {
        SearchSettings settings = new SearchSettings();
        settings.playerClass = playerClass;
        settings.ascensionLevel = ascension;
        settings.speedrunPace = true;
        settings.act4 = false;
        settings.highestFloor = 55;
        settings.forceNeowLament = false;
        settings.neowChoice = 0;
        settings.bossRelicsToTake = new ArrayList<>();
        settings.cardsToBuy = new ArrayList<>();
        settings.relicsToBuy = new ArrayList<>();
        settings.potionsToBuy = new ArrayList<>();
        return settings;
    }

    private static class StartRequest {
        Long seed;
        String playerClass;
        Integer ascensionLevel;
    }

    private static class ChooseRequest {
        Object choice;
    }
}
