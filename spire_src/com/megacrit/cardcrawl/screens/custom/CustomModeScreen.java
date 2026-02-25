package com.megacrit.cardcrawl.screens.custom;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.RunModStrings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;
import com.megacrit.cardcrawl.trials.CustomTrial;
import com.megacrit.cardcrawl.ui.buttons.GridSelectConfirmButton;
import com.megacrit.cardcrawl.ui.panels.SeedPanel;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import java.util.ArrayList;
import java.util.Arrays;

public class CustomModeScreen implements ScrollBarListener {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("CustomModeScreen");
   public static final String[] TEXT;
   private final float imageScale;
   private MenuCancelButton cancelButton = new MenuCancelButton();
   public GridSelectConfirmButton confirmButton;
   private Hitbox controllerHb;
   public ArrayList<CustomModeCharacterButton> options;
   private static float ASC_RIGHT_W;
   public static boolean finalActAvailable = false;
   public boolean isAscensionMode;
   private Hitbox ascensionModeHb;
   private Hitbox ascLeftHb;
   private Hitbox ascRightHb;
   public int ascensionLevel;
   private Hitbox seedHb;
   private String currentSeed;
   private SeedPanel seedPanel;
   private ArrayList<CustomMod> modList;
   private static final String DAILY_MODS = "Daily Mods";
   private static final String MOD_BLIGHT_CHESTS = "Blight Chests";
   private static final String MOD_ONE_HIT_WONDER = "One Hit Wonder";
   private static final String MOD_PRAISE_SNECKO = "Praise Snecko";
   private static final String MOD_INCEPTION = "Inception";
   private static final String MOD_MY_TRUE_FORM = "My True Form";
   private static final String MOD_STARTER_DECK = "Starter Deck";
   private static final String NEUTRAL_COLOR = "b";
   private static final String POSITIVE_COLOR = "g";
   private static final String NEGATIVE_COLOR = "r";
   public boolean screenUp;
   public static float screenX;
   private float ASCENSION_TEXT_Y;
   private boolean grabbedScreen;
   private float grabStartY;
   private float targetY;
   private float scrollY;
   private float scrollLowerBound;
   private float scrollUpperBound;
   private ScrollBar scrollBar;

   public CustomModeScreen() {
      this.confirmButton = new GridSelectConfirmButton(CharacterSelectScreen.TEXT[1]);
      this.options = new ArrayList<>();
      this.isAscensionMode = false;
      this.ascensionLevel = 0;
      this.seedHb = new Hitbox(400.0F * Settings.scale, 90.0F * Settings.scale);
      this.screenUp = false;
      this.ASCENSION_TEXT_Y = 480.0F;
      this.grabbedScreen = false;
      this.grabStartY = 0.0F;
      this.targetY = 0.0F;
      this.scrollY = 0.0F;
      screenX = Settings.isMobile ? 240.0F * Settings.xScale : 300.0F * Settings.xScale;
      this.imageScale = Settings.isMobile ? Settings.scale * 1.2F : Settings.scale;
      this.initializeMods();
      this.initializeCharacters();
      this.calculateScrollBounds();
      if (Settings.isMobile) {
         this.scrollBar = new ScrollBar(
            this, Settings.WIDTH - 280.0F * Settings.xScale - ScrollBar.TRACK_W / 2.0F, Settings.HEIGHT / 2.0F, Settings.HEIGHT - 256.0F * Settings.scale, true
         );
      } else {
         this.scrollBar = new ScrollBar(
            this, Settings.WIDTH - 280.0F * Settings.xScale - ScrollBar.TRACK_W / 2.0F, Settings.HEIGHT / 2.0F, Settings.HEIGHT - 256.0F * Settings.scale
         );
      }

      this.seedPanel = new SeedPanel();
   }

   private void initializeMods() {
      this.modList = new ArrayList<>();
      this.addMod("Daily Mods", "b", false);
      CustomMod draftMod = this.addDailyMod("Draft", "b");
      CustomMod sealedMod = this.addDailyMod("SealedDeck", "b");
      CustomMod endingMod = null;
      if (UnlockTracker.isAchievementUnlocked("THE_ENDING")) {
         endingMod = this.addDailyMod("The Ending", "b");
      }

      CustomMod endlessMod = this.addDailyMod("Endless", "b");
      this.addMod("Blight Chests", "b", false);
      this.addDailyMod("Hoarder", "b");
      CustomMod insanityMod = this.addDailyMod("Insanity", "b");
      this.addDailyMod("Chimera", "b");
      this.addMod("Praise Snecko", "b", false);
      CustomMod shinyMod = this.addDailyMod("Shiny", "b");
      this.addDailyMod("Specialized", "b");
      this.addDailyMod("Vintage", "b");
      this.addDailyMod("ControlledChaos", "b");
      this.addMod("Inception", "b", false);
      this.addDailyMod("Allstar", "g");
      CustomMod diverseMod = this.addDailyMod("Diverse", "g");
      CustomMod redMod = this.addDailyMod("Red Cards", "g");
      CustomMod greenMod = this.addDailyMod("Green Cards", "g");
      CustomMod blueMod = this.addDailyMod("Blue Cards", "g");
      CustomMod purpleMod = null;
      if (!UnlockTracker.isCharacterLocked("Watcher")) {
         purpleMod = this.addDailyMod("Purple Cards", "g");
      }

      this.addDailyMod("Colorless Cards", "g");
      this.addDailyMod("Heirloom", "g");
      this.addDailyMod("Time Dilation", "g");
      this.addDailyMod("Flight", "g");
      this.addMod("My True Form", "g", false);
      this.addDailyMod("DeadlyEvents", "r");
      this.addDailyMod("Binary", "r");
      this.addMod("One Hit Wonder", "r", false);
      this.addDailyMod("Cursed Run", "r");
      this.addDailyMod("Elite Swarm", "r");
      this.addDailyMod("Lethality", "r");
      this.addDailyMod("Midas", "r");
      this.addDailyMod("Night Terrors", "r");
      this.addDailyMod("Terminal", "r");
      this.addDailyMod("Uncertain Future", "r");
      this.addMod("Starter Deck", "r", false);
      if (endingMod != null) {
         endingMod.setMutualExclusionPair(endlessMod);
      }

      insanityMod.setMutualExclusionPair(shinyMod);
      sealedMod.setMutualExclusionPair(draftMod);
      diverseMod.setMutualExclusionPair(redMod);
      diverseMod.setMutualExclusionPair(greenMod);
      diverseMod.setMutualExclusionPair(blueMod);
      if (purpleMod != null) {
         diverseMod.setMutualExclusionPair(purpleMod);
      }
   }

   private CustomMod addMod(String id, String color, boolean isDailyMod) {
      RunModStrings modString = CardCrawlGame.languagePack.getRunModString(id);
      if (modString != null) {
         CustomMod mod = new CustomMod(id, color, isDailyMod);
         this.modList.add(mod);
         return mod;
      } else {
         return null;
      }
   }

   private CustomMod addDailyMod(String id, String color) {
      return this.addMod(id, color, true);
   }

   public void open() {
      this.confirmButton.show();
      this.controllerHb = null;
      this.targetY = 0.0F;
      this.screenUp = true;
      Settings.seed = null;
      Settings.specialSeed = null;
      CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.CUSTOM;
      CardCrawlGame.mainMenuScreen.darken();
      this.cancelButton.show(CharacterSelectScreen.TEXT[5]);
      this.confirmButton.isDisabled = false;
      ASC_RIGHT_W = FontHelper.getSmartWidth(FontHelper.charDescFont, TEXT[4] + "22", 9999.0F, 0.0F) * Settings.xScale;
      this.ascensionModeHb = new Hitbox(80.0F * Settings.scale, 80.0F * Settings.scale);
      this.ascensionModeHb.move(screenX + 130.0F * Settings.xScale, this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale);
      this.ascLeftHb = new Hitbox(95.0F * Settings.scale, 95.0F * Settings.scale);
      this.ascRightHb = new Hitbox(95.0F * Settings.scale, 95.0F * Settings.scale);
      this.ascLeftHb.move(screenX + ASC_RIGHT_W * 1.1F + 250.0F * Settings.xScale, this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale);
      this.ascRightHb.move(screenX + ASC_RIGHT_W * 1.1F + 350.0F * Settings.xScale, this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale);
   }

   public void initializeCharacters() {
      this.options.clear();
      this.options.add(new CustomModeCharacterButton(CardCrawlGame.characterManager.setChosenCharacter(AbstractPlayer.PlayerClass.IRONCLAD), false));
      this.options
         .add(
            new CustomModeCharacterButton(
               CardCrawlGame.characterManager.setChosenCharacter(AbstractPlayer.PlayerClass.THE_SILENT), UnlockTracker.isCharacterLocked("The Silent")
            )
         );
      this.options
         .add(
            new CustomModeCharacterButton(
               CardCrawlGame.characterManager.setChosenCharacter(AbstractPlayer.PlayerClass.DEFECT), UnlockTracker.isCharacterLocked("Defect")
            )
         );
      this.options
         .add(
            new CustomModeCharacterButton(
               CardCrawlGame.characterManager.setChosenCharacter(AbstractPlayer.PlayerClass.WATCHER), UnlockTracker.isCharacterLocked("Watcher")
            )
         );
      int count = this.options.size();

      for (int i = 0; i < count; i++) {
         this.options.get(i).move(screenX + i * 100.0F * Settings.scale - 200.0F * Settings.xScale, this.scrollY - 80.0F * Settings.scale);
      }

      this.options.get(0).hb.clicked = true;
   }

   public void update() {
      this.updateControllerInput();
      if (Settings.isControllerMode && this.controllerHb != null) {
         if (Gdx.input.getY() > Settings.HEIGHT * 0.75F) {
            this.targetY = this.targetY + Settings.SCROLL_SPEED;
         } else if (Gdx.input.getY() < Settings.HEIGHT * 0.25F) {
            this.targetY = this.targetY - Settings.SCROLL_SPEED;
         }
      }

      this.seedPanel.update();
      if (!this.seedPanel.shown) {
         boolean isDraggingScrollBar = this.scrollBar.update();
         if (!isDraggingScrollBar) {
            this.updateScrolling();
         }

         this.updateCharacterButtons();
         this.updateAscension();
         this.updateSeed();
         this.updateMods();
         this.updateEmbarkButton();
         this.updateCancelButton();
      }

      this.currentSeed = SeedHelper.getUserFacingSeedString();
      if (Settings.isControllerMode && this.controllerHb != null) {
         CInputHelper.setCursor(this.controllerHb);
      }
   }

   private void updateCancelButton() {
      this.cancelButton.update();
      if (this.cancelButton.hb.clicked || InputHelper.pressedEscape) {
         InputHelper.pressedEscape = false;
         this.cancelButton.hb.clicked = false;
         this.cancelButton.hide();
         CardCrawlGame.mainMenuScreen.panelScreen.refresh();
      }
   }

   private void updateEmbarkButton() {
      this.confirmButton.update();
      if (this.confirmButton.hb.clicked || CInputActionSet.proceed.isJustPressed()) {
         this.confirmButton.hb.clicked = false;

         for (CustomModeCharacterButton b : this.options) {
            if (b.selected) {
               CardCrawlGame.chosenCharacter = b.c.chosenClass;
               break;
            }
         }

         CardCrawlGame.mainMenuScreen.isFadingOut = true;
         CardCrawlGame.mainMenuScreen.fadeOutMusic();
         Settings.isTrial = true;
         Settings.isDailyRun = false;
         Settings.isEndless = false;
         finalActAvailable = false;
         AbstractDungeon.isAscensionMode = this.isAscensionMode;
         if (!this.isAscensionMode) {
            AbstractDungeon.ascensionLevel = 0;
         } else {
            AbstractDungeon.ascensionLevel = this.ascensionLevel;
         }

         if (this.currentSeed.isEmpty()) {
            long sourceTime = System.nanoTime();
            Random rng = new Random(sourceTime);
            Settings.seed = SeedHelper.generateUnoffensiveSeed(rng);
         }

         AbstractDungeon.generateSeeds();
         CustomTrial trial = new CustomTrial();
         trial.addDailyMods(this.getActiveDailyModIds());
         this.addNonDailyMods(trial, this.getActiveNonDailyMods());
         Settings.isEndless = trial.dailyModIDs().contains("Endless");
         finalActAvailable = trial.dailyModIDs().contains("The Ending");
         CardCrawlGame.trial = trial;
         AbstractPlayer.customMods = CardCrawlGame.trial.dailyModIDs();
      }
   }

   private void updateCharacterButtons() {
      for (int i = 0; i < this.options.size(); i++) {
         if (Settings.isMobile) {
            this.options.get(i).update(screenX + i * 130.0F * Settings.xScale + 130.0F * Settings.scale, this.scrollY + 640.0F * Settings.scale);
         } else {
            this.options.get(i).update(screenX + i * 100.0F * Settings.xScale + 130.0F * Settings.scale, this.scrollY + 640.0F * Settings.scale);
         }
      }
   }

   private void updateSeed() {
      this.seedHb.move(screenX + 280.0F * Settings.xScale, this.scrollY + 320.0F * Settings.scale);
      this.seedHb.update();
      if (this.seedHb.justHovered) {
         this.playHoverSound();
      }

      if (this.seedHb.hovered && InputHelper.justClickedLeft) {
         this.seedHb.clickStarted = true;
      }

      if (this.seedHb.clicked || CInputActionSet.select.isJustPressed() && this.seedHb.hovered) {
         this.seedHb.clicked = false;
         if (Settings.seed == null) {
            Settings.seed = 0L;
         }

         this.seedPanel.show(MainMenuScreen.CurScreen.CUSTOM);
      }
   }

   private void updateAscension() {
      this.ascLeftHb.moveY(this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale);
      this.ascRightHb.moveY(this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale);
      this.ascensionModeHb.moveY(this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale);
      this.ascensionModeHb.update();
      this.ascLeftHb.update();
      this.ascRightHb.update();
      if (this.ascensionModeHb.justHovered || this.ascRightHb.justHovered || this.ascLeftHb.justHovered) {
         this.playHoverSound();
      }

      if (this.ascensionModeHb.hovered && InputHelper.justClickedLeft) {
         this.playClickStartSound();
         this.ascensionModeHb.clickStarted = true;
      } else if (this.ascLeftHb.hovered && InputHelper.justClickedLeft) {
         this.playClickStartSound();
         this.ascLeftHb.clickStarted = true;
      } else if (this.ascRightHb.hovered && InputHelper.justClickedLeft) {
         this.playClickStartSound();
         this.ascRightHb.clickStarted = true;
      }

      if (this.ascensionModeHb.clicked || CInputActionSet.topPanel.isJustPressed()) {
         CInputActionSet.topPanel.unpress();
         this.playClickFinishSound();
         this.ascensionModeHb.clicked = false;
         this.isAscensionMode = !this.isAscensionMode;
         if (this.isAscensionMode && this.ascensionLevel == 0) {
            this.ascensionLevel = 1;
         }
      } else if (this.ascLeftHb.clicked || CInputActionSet.pageLeftViewDeck.isJustPressed()) {
         this.playClickFinishSound();
         this.ascLeftHb.clicked = false;
         this.ascensionLevel--;
         if (this.ascensionLevel < 1) {
            this.ascensionLevel = 20;
         }
      } else if (this.ascRightHb.clicked || CInputActionSet.pageRightViewExhaust.isJustPressed()) {
         this.playClickFinishSound();
         this.ascRightHb.clicked = false;
         this.ascensionLevel++;
         if (this.ascensionLevel > 20) {
            this.ascensionLevel = 1;
         }

         this.isAscensionMode = true;
      }
   }

   private void updateMods() {
      float offset = 0.0F;

      for (int i = 0; i < this.modList.size(); i++) {
         this.modList.get(i).update(this.scrollY + offset);
         offset -= this.modList.get(i).height;
      }
   }

   private ArrayList<String> getActiveDailyModIds() {
      ArrayList<String> active = new ArrayList<>();

      for (CustomMod mod : this.modList) {
         if (mod.selected && mod.isDailyMod) {
            active.add(mod.ID);
         }
      }

      return active;
   }

   private ArrayList<String> getActiveNonDailyMods() {
      ArrayList<String> active = new ArrayList<>();

      for (CustomMod mod : this.modList) {
         if (mod.selected && !mod.isDailyMod) {
            active.add(mod.ID);
         }
      }

      return active;
   }

   private void addNonDailyMods(CustomTrial trial, ArrayList<String> modIds) {
      for (String modId : modIds) {
         switch (modId) {
            case "Daily Mods":
               trial.setRandomDailyMods();
               break;
            case "One Hit Wonder":
               trial.setMaxHpOverride(1);
               break;
            case "Praise Snecko":
               trial.addStarterRelic("Snecko Eye");
               trial.setShouldKeepStarterRelic(false);
               break;
            case "Inception":
               trial.addStarterRelic("Unceasing Top");
               trial.setShouldKeepStarterRelic(false);
               break;
            case "My True Form":
               trial.addStarterCards(Arrays.asList("Demon Form", "Wraith Form v2", "Echo Form", "DevaForm"));
               break;
            case "Starter Deck":
               trial.addStarterRelic("Busted Crown");
               trial.addDailyMod("Binary");
               break;
            case "Blight Chests":
               trial.addDailyMod("Blight Chests");
         }
      }
   }

   private void playClickStartSound() {
      CardCrawlGame.sound.playA("UI_CLICK_1", -0.1F);
   }

   private void playClickFinishSound() {
      CardCrawlGame.sound.playA("UI_CLICK_1", -0.1F);
   }

   private void playHoverSound() {
      CardCrawlGame.sound.playV("UI_HOVER", 0.75F);
   }

   public void render(SpriteBatch sb) {
      this.renderScreen(sb);
      this.scrollBar.render(sb);
      this.cancelButton.render(sb);
      this.confirmButton.render(sb);

      for (CustomModeCharacterButton o : this.options) {
         o.render(sb);
      }

      this.renderAscension(sb);
      this.renderSeed(sb);
      sb.setColor(Color.WHITE);

      for (CustomMod m : this.modList) {
         m.render(sb);
      }

      this.seedPanel.render(sb);
   }

   public void renderScreen(SpriteBatch sb) {
      this.renderTitle(sb, TEXT[0], this.scrollY - 50.0F * Settings.scale);
      this.renderHeader(sb, TEXT[2], this.scrollY - 120.0F * Settings.scale);
      this.renderHeader(sb, TEXT[3], this.scrollY - 290.0F * Settings.scale);
      this.renderHeader(sb, TEXT[7], this.scrollY - 460.0F * Settings.scale);
      this.renderHeader(sb, TEXT[6], this.scrollY - 630.0F * Settings.scale);
   }

   private void renderAscension(SpriteBatch sb) {
      sb.setColor(Color.WHITE);
      if (this.ascensionModeHb.hovered) {
         sb.draw(
            ImageMaster.CHECKBOX,
            this.ascensionModeHb.cX - 32.0F,
            this.ascensionModeHb.cY - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            this.imageScale * 1.2F,
            this.imageScale * 1.2F,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
         sb.setColor(Color.GOLD);
         sb.setBlendFunction(770, 1);
         sb.draw(
            ImageMaster.CHECKBOX,
            this.ascensionModeHb.cX - 32.0F,
            this.ascensionModeHb.cY - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            this.imageScale * 1.2F,
            this.imageScale * 1.2F,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
         sb.setBlendFunction(770, 771);
      } else {
         sb.draw(
            ImageMaster.CHECKBOX,
            this.ascensionModeHb.cX - 32.0F,
            this.ascensionModeHb.cY - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            this.imageScale,
            this.imageScale,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
      }

      if (this.ascensionModeHb.hovered) {
         FontHelper.renderFontCentered(
            sb,
            FontHelper.charDescFont,
            TEXT[4] + this.ascensionLevel,
            screenX + 240.0F * Settings.scale,
            this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale,
            Color.CYAN
         );
      } else {
         FontHelper.renderFontCentered(
            sb,
            FontHelper.charDescFont,
            TEXT[4] + this.ascensionLevel,
            screenX + 240.0F * Settings.scale,
            this.scrollY + this.ASCENSION_TEXT_Y * Settings.scale,
            Settings.BLUE_TEXT_COLOR
         );
      }

      if (this.isAscensionMode) {
         sb.setColor(Color.WHITE);
         sb.draw(
            ImageMaster.TICK,
            this.ascensionModeHb.cX - 32.0F,
            this.ascensionModeHb.cY - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            this.imageScale,
            this.imageScale,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
      }

      if (this.ascensionLevel != 0) {
         FontHelper.renderSmartText(
            sb,
            FontHelper.charDescFont,
            CardCrawlGame.mainMenuScreen.charSelectScreen.ascLevelInfoString = CharacterSelectScreen.A_TEXT[this.ascensionLevel - 1],
            screenX + ASC_RIGHT_W * 1.1F + 400.0F * Settings.scale,
            this.ascensionModeHb.cY + 10.0F * Settings.scale,
            9999.0F,
            32.0F * Settings.scale,
            Settings.CREAM_COLOR
         );
      }

      if (!this.ascLeftHb.hovered && !Settings.isControllerMode) {
         sb.setColor(Color.LIGHT_GRAY);
      } else {
         sb.setColor(Color.WHITE);
      }

      sb.draw(
         ImageMaster.CF_LEFT_ARROW,
         this.ascLeftHb.cX - 24.0F,
         this.ascLeftHb.cY - 24.0F,
         24.0F,
         24.0F,
         48.0F,
         48.0F,
         this.imageScale,
         this.imageScale,
         0.0F,
         0,
         0,
         48,
         48,
         false,
         false
      );
      if (!this.ascRightHb.hovered && !Settings.isControllerMode) {
         sb.setColor(Color.LIGHT_GRAY);
      } else {
         sb.setColor(Color.WHITE);
      }

      sb.draw(
         ImageMaster.CF_RIGHT_ARROW,
         this.ascRightHb.cX - 24.0F,
         this.ascRightHb.cY - 24.0F,
         24.0F,
         24.0F,
         48.0F,
         48.0F,
         this.imageScale,
         this.imageScale,
         0.0F,
         0,
         0,
         48,
         48,
         false,
         false
      );
      if (Settings.isControllerMode) {
         sb.draw(
            CInputActionSet.topPanel.getKeyImg(),
            this.ascensionModeHb.cX - 64.0F * Settings.scale - 32.0F,
            this.ascensionModeHb.cY - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            Settings.scale,
            Settings.scale,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
         sb.draw(
            CInputActionSet.pageLeftViewDeck.getKeyImg(),
            this.ascLeftHb.cX - 12.0F * Settings.scale - 32.0F,
            this.ascLeftHb.cY + 40.0F * Settings.scale - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            Settings.scale,
            Settings.scale,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
         sb.draw(
            CInputActionSet.pageRightViewExhaust.getKeyImg(),
            this.ascRightHb.cX + 12.0F * Settings.scale - 32.0F,
            this.ascRightHb.cY + 40.0F * Settings.scale - 32.0F,
            32.0F,
            32.0F,
            64.0F,
            64.0F,
            Settings.scale,
            Settings.scale,
            0.0F,
            0,
            0,
            64,
            64,
            false,
            false
         );
      }

      this.ascensionModeHb.render(sb);
      this.ascLeftHb.render(sb);
      this.ascRightHb.render(sb);
   }

   private void renderSeed(SpriteBatch sb) {
      if (this.seedHb.hovered) {
         FontHelper.renderSmartText(
            sb,
            FontHelper.panelNameFont,
            TEXT[8] + ": " + this.currentSeed,
            screenX + 96.0F * Settings.scale,
            this.seedHb.cY,
            9999.0F,
            32.0F * Settings.scale,
            Settings.GREEN_TEXT_COLOR
         );
      } else {
         FontHelper.renderSmartText(
            sb,
            FontHelper.turnNumFont,
            TEXT[8] + ": " + this.currentSeed,
            screenX + 96.0F * Settings.scale,
            this.seedHb.cY,
            9999.0F,
            32.0F * Settings.scale,
            Settings.BLUE_TEXT_COLOR
         );
      }

      this.seedHb.render(sb);
   }

   private void renderHeader(SpriteBatch sb, String text, float y) {
      if (Settings.isMobile) {
         FontHelper.renderSmartText(
            sb,
            FontHelper.panelNameFont,
            text,
            screenX + 50.0F * Settings.scale,
            y + 850.0F * Settings.scale,
            9999.0F,
            32.0F * Settings.scale,
            Settings.GOLD_COLOR,
            1.2F
         );
      } else {
         FontHelper.renderSmartText(
            sb,
            FontHelper.panelNameFont,
            text,
            screenX + 50.0F * Settings.scale,
            y + 850.0F * Settings.scale,
            9999.0F,
            32.0F * Settings.scale,
            Settings.GOLD_COLOR
         );
      }
   }

   private void renderTitle(SpriteBatch sb, String text, float y) {
      FontHelper.renderSmartText(sb, FontHelper.charTitleFont, text, screenX, y + 900.0F * Settings.scale, 9999.0F, 32.0F * Settings.scale, Settings.GOLD_COLOR);
      if (!Settings.usesTrophies) {
         FontHelper.renderSmartText(
            sb,
            FontHelper.tipBodyFont,
            TEXT[1],
            screenX + FontHelper.getSmartWidth(FontHelper.charTitleFont, text, 9999.0F, 9999.0F) + 18.0F * Settings.scale,
            y + 888.0F * Settings.scale,
            9999.0F,
            32.0F * Settings.scale,
            Settings.RED_TEXT_COLOR
         );
      } else {
         FontHelper.renderSmartText(
            sb,
            FontHelper.tipBodyFont,
            TEXT[9],
            screenX + FontHelper.getSmartWidth(FontHelper.charTitleFont, text, 9999.0F, 9999.0F) + 18.0F * Settings.scale,
            y + 888.0F * Settings.scale,
            9999.0F,
            32.0F * Settings.scale,
            Settings.RED_TEXT_COLOR
         );
      }
   }

   private void updateControllerInput() {
      if (Settings.isControllerMode) {
         CustomModeScreen.CSelectionType type = CustomModeScreen.CSelectionType.CHARACTER;
         boolean anyHovered = false;
         int index = 0;

         for (CustomModeCharacterButton b : this.options) {
            if (b.hb.hovered) {
               anyHovered = true;
               break;
            }

            index++;
         }

         if (!anyHovered && this.ascensionModeHb.hovered) {
            anyHovered = true;
            type = CustomModeScreen.CSelectionType.ASCENSION;
         }

         if (!anyHovered && this.seedHb.hovered) {
            anyHovered = true;
            type = CustomModeScreen.CSelectionType.SEED;
         }

         if (!anyHovered) {
            index = 0;

            for (CustomMod m : this.modList) {
               if (m.hb.hovered) {
                  anyHovered = true;
                  type = CustomModeScreen.CSelectionType.MODIFIERS;
                  break;
               }

               index++;
            }
         }

         if (!anyHovered && this.controllerHb == null) {
            CInputHelper.setCursor(this.options.get(0).hb);
            this.controllerHb = this.options.get(0).hb;
         } else {
            switch (type) {
               case CHARACTER:
                  if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                     if (++index > this.options.size() - 1) {
                        index = this.options.size() - 1;
                     }

                     CInputHelper.setCursor(this.options.get(index).hb);
                     this.controllerHb = this.options.get(index).hb;
                  } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                     if (--index < 0) {
                        index = 0;
                     }

                     CInputHelper.setCursor(this.options.get(index).hb);
                     this.controllerHb = this.options.get(index).hb;
                  } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                     CInputHelper.setCursor(this.ascensionModeHb);
                     this.controllerHb = this.ascensionModeHb;
                  } else if (CInputActionSet.select.isJustPressed()) {
                     CInputActionSet.select.unpress();
                     this.options.get(index).hb.clicked = true;
                  }
                  break;
               case ASCENSION:
                  if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                     CInputHelper.setCursor(this.options.get(0).hb);
                     this.controllerHb = this.options.get(0).hb;
                  } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                     CInputHelper.setCursor(this.seedHb);
                     this.controllerHb = this.seedHb;
                  }
                  break;
               case SEED:
                  if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                     CInputHelper.setCursor(this.ascensionModeHb);
                     this.controllerHb = this.ascensionModeHb;
                  } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                     CInputHelper.setCursor(this.modList.get(0).hb);
                     this.controllerHb = this.modList.get(0).hb;
                  }
                  break;
               case MODIFIERS:
                  if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                     if (--index < 0) {
                        CInputHelper.setCursor(this.seedHb);
                        this.controllerHb = this.seedHb;
                     } else {
                        CInputHelper.setCursor(this.modList.get(index).hb);
                        this.controllerHb = this.modList.get(index).hb;
                     }
                  } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                     if (++index > this.modList.size() - 1) {
                        index = this.modList.size() - 1;
                     }

                     CInputHelper.setCursor(this.modList.get(index).hb);
                     this.controllerHb = this.modList.get(index).hb;
                  } else if (CInputActionSet.select.isJustPressed()) {
                     CInputActionSet.select.unpress();
                     this.modList.get(index).hb.clicked = true;
                  }
            }
         }
      }
   }

   private void updateScrolling() {
      int y = InputHelper.mY;
      if (this.scrollUpperBound > 0.0F) {
         if (!this.grabbedScreen) {
            if (InputHelper.scrolledDown) {
               this.targetY = this.targetY + Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
               this.targetY = this.targetY - Settings.SCROLL_SPEED;
            }

            if (InputHelper.justClickedLeft) {
               this.grabbedScreen = true;
               this.grabStartY = y - this.targetY;
            }
         } else if (InputHelper.isMouseDown) {
            this.targetY = y - this.grabStartY;
         } else {
            this.grabbedScreen = false;
         }
      }

      this.scrollY = MathHelper.scrollSnapLerpSpeed(this.scrollY, this.targetY);
      if (this.targetY < this.scrollLowerBound) {
         this.targetY = MathHelper.scrollSnapLerpSpeed(this.targetY, this.scrollLowerBound);
      } else if (this.targetY > this.scrollUpperBound) {
         this.targetY = MathHelper.scrollSnapLerpSpeed(this.targetY, this.scrollUpperBound);
      }

      this.updateBarPosition();
   }

   private void calculateScrollBounds() {
      this.scrollUpperBound = this.modList.size() * 90.0F * Settings.scale + 270.0F * Settings.scale;
      this.scrollLowerBound = 100.0F * Settings.scale;
   }

   @Override
   public void scrolledUsingBar(float newPercent) {
      float newPosition = MathHelper.valueFromPercentBetween(this.scrollLowerBound, this.scrollUpperBound, newPercent);
      this.scrollY = newPosition;
      this.targetY = newPosition;
      this.updateBarPosition();
   }

   private void updateBarPosition() {
      float percent = MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound, this.scrollY);
      this.scrollBar.parentScrolledToPercent(percent);
   }

   public void deselectOtherOptions(CustomModeCharacterButton characterOption) {
      for (CustomModeCharacterButton o : this.options) {
         if (o != characterOption) {
            o.selected = false;
         }
      }
   }

   static {
      TEXT = uiStrings.TEXT;
   }

   private static enum CSelectionType {
      CHARACTER,
      ASCENSION,
      SEED,
      MODIFIERS;
   }
}
