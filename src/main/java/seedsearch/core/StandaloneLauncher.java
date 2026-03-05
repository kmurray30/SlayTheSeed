package seedsearch.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.metrics.MetricData;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.integrations.DistributorFactory;
import com.megacrit.cardcrawl.localization.LocalizedStrings;
import com.megacrit.cardcrawl.screens.SingleCardViewPopup;
import com.megacrit.cardcrawl.screens.SingleRelicViewPopup;
import com.megacrit.cardcrawl.ui.buttons.CancelButton;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Runs minimal game initialization for standalone seed search. Replicates the
 * essential parts of CardCrawlGame.create() that SeedRunner depends on.
 * Uses reflection to dispatch to web, regression, or search so core has no compile-time deps on them.
 */
public class StandaloneLauncher {

    public static void runStandalone(CardCrawlGame game) {
        try {
            Settings.displayOptions = new ArrayList<>();
            Settings.displayOptions.add(new com.megacrit.cardcrawl.screens.DisplayOption(0, 0));

            // Use EA integration (no-op, no native libs) - Steam/Discord have x86-only natives, fail on arm64 Mac
            CardCrawlGame.publisherIntegration = DistributorFactory.getEnabledDistributor("ea");

            CardCrawlGame.saveSlotPref = SaveHelper.getPrefs("STSSaveSlots");
            CardCrawlGame.saveSlot = CardCrawlGame.saveSlotPref.getInteger("DEFAULT_SLOT", 0);
            CardCrawlGame.playerPref = SaveHelper.getPrefs("STSPlayer");
            CardCrawlGame.playerName = CardCrawlGame.saveSlotPref.getString(SaveHelper.slotName("PROFILE_NAME", CardCrawlGame.saveSlot), "");
            if (CardCrawlGame.playerName.equals("")) {
                CardCrawlGame.playerName = CardCrawlGame.playerPref.getString("name", "");
            }
            if ((CardCrawlGame.alias = CardCrawlGame.playerPref.getString("alias", "")).equals("")) {
                CardCrawlGame.alias = CardCrawlGame.generateRandomAlias();
                CardCrawlGame.playerPref.putString("alias", CardCrawlGame.alias);
                CardCrawlGame.playerPref.flush();
            }

            Settings.initialize(false);

            CardCrawlGame.metricData = new MetricData();

            OrthographicCamera camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            camera.position.set(camera.viewportWidth / 2.0f, camera.viewportHeight / 2.0f, 0.0f);
            camera.update();
            Field cameraField = CardCrawlGame.class.getDeclaredField("camera");
            cameraField.setAccessible(true);
            cameraField.set(game, camera);
            CardCrawlGame.viewport = new FitViewport(Settings.WIDTH, (float) Settings.HEIGHT, camera);
            CardCrawlGame.viewport.apply();

            CardCrawlGame.languagePack = new LocalizedStrings();
            CardCrawlGame.tips = new com.megacrit.cardcrawl.helpers.GameTips();
            CardCrawlGame.cardPopup = new SingleCardViewPopup();
            CardCrawlGame.relicPopup = new SingleRelicViewPopup();

            Pixmap blankPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            blankPixmap.setColor(0x00000000);
            blankPixmap.fill();
            Gdx.graphics.setCursor(Gdx.graphics.newCursor(blankPixmap, 0, 0));
            blankPixmap.dispose();

            Field sbField = CardCrawlGame.class.getDeclaredField("sb");
            sbField.setAccessible(true);
            sbField.set(game, new SpriteBatch());
            CardCrawlGame.psb = new PolygonSpriteBatch();
            CardCrawlGame.music = new com.megacrit.cardcrawl.audio.MusicMaster();
            CardCrawlGame.sound = new com.megacrit.cardcrawl.audio.SoundMaster();

            com.megacrit.cardcrawl.core.AbstractCreature.initialize();
            com.megacrit.cardcrawl.cards.AbstractCard.initialize();
            GameDictionary.initialize();
            ImageMaster.initialize();
            com.megacrit.cardcrawl.powers.AbstractPower.initialize();
            FontHelper.initialize();
            com.megacrit.cardcrawl.cards.AbstractCard.initializeDynamicFrameWidths();
            UnlockTracker.initialize();
            CardLibrary.initialize();
            RelicLibrary.initialize();
            InputHelper.initialize();
            TipTracker.initialize();
            ModHelper.initialize();
            ShaderHelper.initializeShaders();
            UnlockTracker.retroactiveUnlock();
            com.megacrit.cardcrawl.helpers.controller.CInputHelper.loadSettings();

            CardCrawlGame.characterManager = new com.megacrit.cardcrawl.characters.CharacterManager();

            if ("true".equals(System.getProperty("seedsearch.web"))) {
                invokeWebAppLauncher();
            } else if ("true".equals(System.getProperty("seedsearch.regression.test"))) {
                invokeRegressionTest();
            } else {
                invokeSeedSearch();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.exit();
        }
    }

    /** Dispatch via reflection so core has no compile-time dependency on web_app. */
    private static void invokeWebAppLauncher() {
        try {
            Class<?> launcherClass = Class.forName("seedsearch.seed_explorer.web_app.WebAppLauncher");
            Method startServer = launcherClass.getMethod("startServer");
            startServer.invoke(null);
        } catch (ClassNotFoundException e) {
            System.err.println("Web app not available. Build with web_app module.");
            e.printStackTrace();
            Gdx.app.exit();
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.exit();
        }
    }

    /** Dispatch via reflection so core has no compile-time dependency on seed_search. */
    private static void invokeRegressionTest() {
        try {
            Class<?> testClass = Class.forName("seedsearch.seed_search.RegressionTest");
            Method run = testClass.getMethod("run");
            run.invoke(null);
        } catch (ClassNotFoundException e) {
            System.err.println("Regression test not available.");
            e.printStackTrace();
            Gdx.app.exit();
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.exit();
        }
    }

    /** Dispatch via reflection so core has no compile-time dependency on seed_search. */
    private static void invokeSeedSearch() {
        try {
            Class<?> searchClass = Class.forName("seedsearch.seed_search.SeedSearch");
            Method search = searchClass.getMethod("search");
            // Set loadingEnabled = false via reflection before calling search
            Field loadingEnabled = searchClass.getField("loadingEnabled");
            loadingEnabled.setBoolean(null, false);
            search.invoke(null);
        } catch (ClassNotFoundException e) {
            System.err.println("Seed search not available.");
            e.printStackTrace();
            Gdx.app.exit();
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.exit();
        }
    }
}
