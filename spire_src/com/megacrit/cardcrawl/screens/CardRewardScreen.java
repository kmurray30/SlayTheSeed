package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.TipTracker;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.mainMenu.HorizontalScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;
import com.megacrit.cardcrawl.ui.FtueTip;
import com.megacrit.cardcrawl.ui.buttons.ConfirmButton;
import com.megacrit.cardcrawl.ui.buttons.PeekButton;
import com.megacrit.cardcrawl.ui.buttons.SingingBowlButton;
import com.megacrit.cardcrawl.ui.buttons.SkipCardButton;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.FastCardObtainEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;
import de.robojumper.ststwitch.TwitchPanel;
import de.robojumper.ststwitch.TwitchVoteListener;
import de.robojumper.ststwitch.TwitchVoteOption;
import de.robojumper.ststwitch.TwitchVoter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CardRewardScreen implements ScrollBarListener {
   private static final Logger logger = LogManager.getLogger(CardRewardScreen.class.getName());
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("CardRewardScreen");
   public static final String[] TEXT;
   private static final float PAD_X = 40.0F * Settings.xScale;
   private static final float CARD_TARGET_Y = Settings.HEIGHT * 0.45F;
   public ArrayList<AbstractCard> rewardGroup;
   public AbstractCard discoveryCard = null;
   public boolean hasTakenAll = false;
   public boolean cardOnly = false;
   public RewardItem rItem = null;
   private boolean draft = false;
   private boolean discovery = false;
   private boolean chooseOne = false;
   private boolean skippable = false;
   private String header = "";
   private SkipCardButton skipButton = new SkipCardButton();
   private SingingBowlButton bowlButton = new SingingBowlButton();
   private PeekButton peekButton = new PeekButton();
   private final int SKIP_BUTTON_IDX = 0;
   private final int BOWL_BUTTON_IDX = 1;
   private int draftCount = 0;
   private static final int MAX_CARDS_ON_SCREEN = 4;
   private static final int MAX_CARDS_BEFORE_SCROLL = 5;
   private static final float START_X = Settings.WIDTH - 300.0F * Settings.xScale;
   private boolean grabbedScreen = false;
   private float grabStartX = 0.0F;
   private float scrollX = START_X;
   private float targetX = this.scrollX;
   private float scrollLowerBound = Settings.WIDTH - 300.0F * Settings.xScale;
   private float scrollUpperBound = 2400.0F * Settings.scale;
   private HorizontalScrollBar scrollBar;
   public ConfirmButton confirmButton = new ConfirmButton();
   private AbstractCard touchCard = null;
   @Deprecated
   private boolean codex = false;
   @Deprecated
   public AbstractCard codexCard = null;
   private boolean isVoting = false;
   private boolean mayVote = false;

   public CardRewardScreen() {
      this.scrollBar = new HorizontalScrollBar(
         this, Settings.WIDTH / 2.0F, 50.0F * Settings.scale + HorizontalScrollBar.TRACK_H / 2.0F, Settings.WIDTH - 256.0F * Settings.scale
      );
   }

   public void update() {
      if (Settings.isTouchScreen) {
         this.confirmButton.update();
         if (this.confirmButton.hb.clicked && this.touchCard != null) {
            this.confirmButton.hb.clicked = false;
            this.confirmButton.hb.clickStarted = false;
            this.confirmButton.isDisabled = true;
            this.confirmButton.hide();
            this.touchCard.hb.clicked = false;
            this.skipButton.hide();
            this.bowlButton.hide();
            if (this.chooseOne) {
               this.touchCard.onChoseThisOption();
            } else if (this.codex) {
               this.codexCard = this.touchCard;
            } else if (this.discovery) {
               this.discoveryCard = this.touchCard;
            } else {
               this.acquireCard(this.touchCard);
            }

            this.takeReward();
            if (this.draft && this.draftCount < 15) {
               this.draftOpen();
            } else {
               AbstractDungeon.closeCurrentScreen();
               this.draftCount = 0;
            }

            this.touchCard = null;
         }
      }

      this.peekButton.update();
      if (!PeekButton.isPeeking) {
         this.skipButton.update();
         this.bowlButton.update();
      }

      this.updateControllerInput();
      if (!PeekButton.isPeeking) {
         if (!this.scrollBar.update()) {
            this.updateScrolling();
         }

         this.cardSelectUpdate();
      }
   }

   private void updateControllerInput() {
      if (Settings.isControllerMode
         && !AbstractDungeon.topPanel.selectPotionMode
         && AbstractDungeon.topPanel.potionUi.isHidden
         && !AbstractDungeon.player.viewingRelics) {
         int index = 0;
         boolean anyHovered = false;

         for (AbstractCard c : this.rewardGroup) {
            if (c.hb.hovered) {
               anyHovered = true;
               break;
            }

            index++;
         }

         if (!anyHovered) {
            int var5 = 0;
            Gdx.input.setCursorPosition((int)this.rewardGroup.get(var5).hb.cX, Settings.HEIGHT - (int)this.rewardGroup.get(var5).hb.cY);
         } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
            if (--index < 0) {
               index = this.rewardGroup.size() - 1;
            }

            Gdx.input.setCursorPosition((int)this.rewardGroup.get(index).hb.cX, Settings.HEIGHT - (int)this.rewardGroup.get(index).hb.cY);
         } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
            if (++index > this.rewardGroup.size() - 1) {
               index = 0;
            }

            Gdx.input.setCursorPosition((int)this.rewardGroup.get(index).hb.cX, Settings.HEIGHT - (int)this.rewardGroup.get(index).hb.cY);
         }
      }
   }

   private void updateScrolling() {
      int x = InputHelper.mX;
      if (!this.grabbedScreen) {
         if (InputHelper.scrolledDown) {
            this.targetX = this.targetX + Settings.SCROLL_SPEED;
         } else if (InputHelper.scrolledUp) {
            this.targetX = this.targetX - Settings.SCROLL_SPEED;
         }

         if (InputHelper.justClickedLeft) {
            this.grabbedScreen = true;
            this.grabStartX = -x - this.targetX;
         }
      } else if (InputHelper.isMouseDown) {
         this.targetX = -x - this.grabStartX;
      } else {
         this.grabbedScreen = false;
      }

      this.scrollX = MathHelper.scrollSnapLerpSpeed(this.scrollX, this.targetX);
      this.resetScrolling();
      this.updateBarPosition();
   }

   private void resetScrolling() {
      if (this.targetX < this.scrollLowerBound) {
         this.targetX = MathHelper.scrollSnapLerpSpeed(this.targetX, this.scrollLowerBound);
      } else if (this.targetX > this.scrollUpperBound) {
         this.targetX = MathHelper.scrollSnapLerpSpeed(this.targetX, this.scrollUpperBound);
      }
   }

   private void cardSelectUpdate() {
      AbstractCard hoveredCard = null;

      for (AbstractCard c : this.rewardGroup) {
         c.update();
         c.updateHoverLogic();
         if (c.hb.justHovered) {
            CardCrawlGame.sound.playV("CARD_OBTAIN", 0.4F);
         }

         if (c.hb.hovered) {
            hoveredCard = c;
         }
      }

      if (hoveredCard != null && InputHelper.justClickedLeft) {
         hoveredCard.hb.clickStarted = true;
      }

      if (hoveredCard != null && (InputHelper.justClickedRight || CInputActionSet.proceed.isJustPressed())) {
         InputHelper.justClickedRight = false;
         CardCrawlGame.cardPopup.open(hoveredCard);
      }

      if (hoveredCard != null && CInputActionSet.select.isJustPressed()) {
         hoveredCard.hb.clicked = true;
      }

      if (hoveredCard != null && hoveredCard.hb.clicked) {
         hoveredCard.hb.clicked = false;
         if (!Settings.isTouchScreen) {
            this.skipButton.hide();
            this.bowlButton.hide();
            if (this.codex) {
               this.codexCard = hoveredCard;
            } else if (this.discovery) {
               this.discoveryCard = hoveredCard;
            } else if (this.chooseOne) {
               hoveredCard.onChoseThisOption();
               AbstractDungeon.effectList.add(new ExhaustCardEffect(hoveredCard));
            } else {
               this.acquireCard(hoveredCard);
            }

            this.takeReward();
            if (this.draft && this.draftCount < 15) {
               this.draftOpen();
            } else {
               AbstractDungeon.closeCurrentScreen();
               this.draftCount = 0;
            }
         } else if (!this.confirmButton.hb.clicked) {
            this.touchCard = hoveredCard;
            this.confirmButton.show();
            this.confirmButton.isDisabled = false;
         }
      }

      if (InputHelper.justReleasedClickLeft
         && Settings.isTouchScreen
         && hoveredCard == null
         && !this.confirmButton.isDisabled
         && !this.confirmButton.hb.hovered) {
         this.confirmButton.hb.clickStarted = false;
         this.confirmButton.hide();
         this.touchCard = null;
      }
   }

   private void acquireCard(AbstractCard hoveredCard) {
      this.recordMetrics(hoveredCard);
      InputHelper.justClickedLeft = false;
      AbstractDungeon.effectsQueue.add(new FastCardObtainEffect(hoveredCard, hoveredCard.current_x, hoveredCard.current_y));
   }

   private void recordMetrics(AbstractCard hoveredCard) {
      HashMap<String, Object> choice = new HashMap<>();
      ArrayList<String> notpicked = new ArrayList<>();

      for (AbstractCard card : this.rewardGroup) {
         if (card != hoveredCard) {
            notpicked.add(card.getMetricID());
         }
      }

      choice.put("picked", hoveredCard.getMetricID());
      choice.put("not_picked", notpicked);
      choice.put("floor", AbstractDungeon.floorNum);
      CardCrawlGame.metricData.card_choices.add(choice);
   }

   private void recordMetrics(String pickText) {
      HashMap<String, Object> choice = new HashMap<>();
      ArrayList<String> notpicked = new ArrayList<>();

      for (AbstractCard card : this.rewardGroup) {
         notpicked.add(card.getMetricID());
      }

      choice.put("picked", pickText);
      choice.put("not_picked", notpicked);
      choice.put("floor", AbstractDungeon.floorNum);
      CardCrawlGame.metricData.card_choices.add(choice);
   }

   private void takeReward() {
      if (this.rItem != null) {
         AbstractDungeon.combatRewardScreen.rewards.remove(this.rItem);
         AbstractDungeon.combatRewardScreen.positionRewards();
         if (AbstractDungeon.combatRewardScreen.rewards.isEmpty()) {
            AbstractDungeon.combatRewardScreen.hasTakenAll = true;
            AbstractDungeon.overlayMenu.proceedButton.show();
         }
      }
   }

   public void completeVoting(int option) {
      if (this.isVoting) {
         this.isVoting = false;
         if (this.getVoter().isPresent()) {
            TwitchVoter twitchVoter = this.getVoter().get();
            AbstractDungeon.topPanel
               .twitch
               .ifPresent(twitchPanel -> twitchPanel.connection.sendMessage("Voting on card ended... chose " + twitchVoter.getOptions()[option].displayName));
         }

         while (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.CARD_REWARD) {
            AbstractDungeon.closeCurrentScreen();
         }

         if (option != 0) {
            if (!this.bowlButton.isHidden() && option == 1) {
               this.bowlButton.onClick();
            } else if (!this.bowlButton.isHidden()) {
               AbstractDungeon.overlayMenu.cancelButton.hide();
               this.acquireCard(this.rewardGroup.get(option - 2));
            } else if (option < this.rewardGroup.size() + 1) {
               AbstractDungeon.overlayMenu.cancelButton.hide();
               this.acquireCard(this.rewardGroup.get(option - 1));
            }
         }

         this.takeReward();
         AbstractDungeon.closeCurrentScreen();
      }
   }

   private void renderTwitchVotes(SpriteBatch sb) {
      if (this.isVoting) {
         if (this.getVoter().isPresent()) {
            TwitchVoter twitchVoter = this.getVoter().get();
            TwitchVoteOption[] options = twitchVoter.getOptions();
            int voteCountOffset = this.bowlButton.isHidden() ? 1 : 2;
            int sum = Arrays.stream(options).map(c -> c.voteCount).reduce(0, Integer::sum);

            for (int i = 0; i < this.rewardGroup.size(); i++) {
               AbstractCard c = this.rewardGroup.get(i);
               StringBuilder cardVoteText = new StringBuilder("#").append(i + voteCountOffset).append(": ").append(options[i + voteCountOffset].voteCount);
               if (sum > 0) {
                  cardVoteText.append(" (").append(options[i + voteCountOffset].voteCount * 100 / sum).append("%)");
               }

               FontHelper.renderFontCentered(
                  sb, FontHelper.panelNameFont, cardVoteText.toString(), c.target_x, c.target_y - 200.0F * Settings.scale, Color.WHITE.cpy()
               );
            }

            StringBuilder skipVoteText = new StringBuilder("#0: ").append(options[0].voteCount);
            if (sum > 0) {
               skipVoteText.append(" (").append(options[0].voteCount * 100 / sum).append("%)");
            }

            if (this.bowlButton.isHidden()) {
               FontHelper.renderFontCentered(
                  sb, FontHelper.panelNameFont, skipVoteText.toString(), Settings.WIDTH / 2.0F, 150.0F * Settings.scale, Color.WHITE.cpy()
               );
            } else {
               FontHelper.renderFontCentered(
                  sb, FontHelper.panelNameFont, skipVoteText.toString(), Settings.WIDTH / 2.0F - 100.0F, 150.0F * Settings.scale, Color.WHITE.cpy()
               );
               StringBuilder bowlVoteText = new StringBuilder("#1: ").append(options[1].voteCount);
               if (sum > 0) {
                  bowlVoteText.append(" (").append(options[1].voteCount * 100 / sum).append("%)");
               }

               FontHelper.renderFontCentered(
                  sb, FontHelper.panelNameFont, bowlVoteText.toString(), Settings.WIDTH / 2.0F + 100.0F, 150.0F * Settings.scale, Color.WHITE.cpy()
               );
            }

            FontHelper.renderFontCentered(
               sb,
               FontHelper.panelNameFont,
               TEXT[3] + twitchVoter.getSecondsRemaining() + TEXT[4],
               Settings.WIDTH / 2.0F,
               AbstractDungeon.dynamicBanner.y - 70.0F * Settings.scale,
               Color.WHITE.cpy()
            );
         }
      }
   }

   public void render(SpriteBatch sb) {
      this.peekButton.render(sb);
      if (!PeekButton.isPeeking) {
         this.confirmButton.render(sb);
         this.skipButton.render(sb);
         this.bowlButton.render(sb);
         this.renderCardReward(sb);
         if (this.shouldShowScrollBar()) {
            this.scrollBar.render(sb);
         }

         if (!this.codex && !this.discovery) {
            this.renderTwitchVotes(sb);
         }
      }

      if (this.codex || this.discovery) {
         AbstractDungeon.overlayMenu.combatDeckPanel.render(sb);
         AbstractDungeon.overlayMenu.discardPilePanel.render(sb);
         AbstractDungeon.overlayMenu.exhaustPanel.render(sb);
      }
   }

   private void renderCardReward(SpriteBatch sb) {
      if (this.rewardGroup.size() > 5) {
         int maxPossibleStartingIndex = this.rewardGroup.size() - 4;
         int indexToStartAt = Math.max(
            Math.min(
               (int)((maxPossibleStartingIndex + 1) * MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound, this.scrollX)),
               maxPossibleStartingIndex
            ),
            0
         );

         for (AbstractCard c : this.rewardGroup) {
            if (this.rewardGroup.indexOf(c) >= indexToStartAt && this.rewardGroup.indexOf(c) < indexToStartAt + 4) {
               c.target_x = Settings.WIDTH / 2.0F + (this.rewardGroup.indexOf(c) - indexToStartAt - 1.5F) * (AbstractCard.IMG_WIDTH + PAD_X);
            } else if (this.rewardGroup.indexOf(c) < indexToStartAt) {
               c.target_x = -Settings.WIDTH * 0.25F;
            } else {
               c.target_x = Settings.WIDTH * 1.25F;
            }

            c.target_y = Settings.HEIGHT / 2.0F;
         }
      } else {
         for (AbstractCard c : this.rewardGroup) {
            c.target_x = Settings.WIDTH / 2.0F + (this.rewardGroup.indexOf(c) - (this.rewardGroup.size() - 1) / 2.0F) * (AbstractCard.IMG_WIDTH + PAD_X);
            c.target_y = CARD_TARGET_Y;
         }
      }

      for (AbstractCard c : this.rewardGroup) {
         c.render(sb);
      }

      for (AbstractCard c : this.rewardGroup) {
         c.renderCardTip(sb);
      }
   }

   public void reopen() {
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      if (!this.draft && !this.codex && !this.discovery && !this.chooseOne) {
         if (AbstractDungeon.player.hasRelic("Singing Bowl")) {
            if (this.skippable) {
               this.skipButton.show(true);
            } else {
               this.skipButton.hide();
            }

            this.bowlButton.show(this.rItem);
         } else {
            if (this.skippable) {
               this.skipButton.show();
            } else {
               this.skipButton.hide();
            }

            this.bowlButton.hide();
         }
      } else {
         if (this.skippable) {
            this.skipButton.show();
         } else {
            this.skipButton.hide();
         }

         this.bowlButton.hide();
      }

      AbstractDungeon.topPanel.unhoverHitboxes();
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.dynamicBanner.appear(this.header);
      AbstractDungeon.overlayMenu.proceedButton.hide();
      this.skipButton.screenDisabled = true;
   }

   public void open(ArrayList<AbstractCard> cards, RewardItem rItem, String header) {
      if (System.getProperty("seedsearch.standalone") != null) {
         seedsearch.core.StandaloneHooks.rewardCards = cards;
      }
      this.peekButton.hideInstantly();
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      this.codex = false;
      this.discovery = false;
      this.chooseOne = false;
      this.draft = false;
      this.rItem = rItem;
      this.skippable = true;
      if (AbstractDungeon.player.hasRelic("Singing Bowl")) {
         this.skipButton.show(true);
         this.bowlButton.show(rItem);
      } else {
         this.skipButton.show();
         this.bowlButton.hide();
      }

      AbstractDungeon.topPanel.unhoverHitboxes();
      this.rewardGroup = cards;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      this.header = header;
      AbstractDungeon.dynamicBanner.appear(header);
      AbstractDungeon.overlayMenu.proceedButton.hide();
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard c : cards) {
         UnlockTracker.markCardAsSeen(c.cardID);
      }

      for (AbstractCard c : cards) {
         if (c.type == AbstractCard.CardType.POWER && !TipTracker.tips.get("POWER_TIP")) {
            AbstractDungeon.ftue = new FtueTip(
               AbstractPlayer.LABEL[0], AbstractPlayer.MSG[0], Settings.WIDTH / 2.0F - 500.0F * Settings.scale, Settings.HEIGHT / 2.0F, c
            );
            AbstractDungeon.ftue.type = FtueTip.TipType.POWER;
            TipTracker.neverShowAgain("POWER_TIP");
            this.skipButton.hide();
            this.bowlButton.hide();
            break;
         }
      }

      this.mayVote = true;
      if (AbstractDungeon.topPanel.twitch.isPresent()) {
         this.updateVote();
      }
   }

   public void customCombatOpen(ArrayList<AbstractCard> choices, String text, boolean skippable) {
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      this.rItem = null;
      this.codex = false;
      this.discovery = true;
      this.chooseOne = false;
      this.discoveryCard = null;
      this.draft = false;
      this.codexCard = null;
      this.header = text;
      this.peekButton.hideInstantly();
      this.peekButton.show();
      this.skippable = skippable;
      if (skippable) {
         this.skipButton.show();
      } else {
         this.skipButton.hide();
      }

      this.bowlButton.hide();
      AbstractDungeon.topPanel.unhoverHitboxes();
      this.rewardGroup = choices;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(this.header);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard c : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(c.cardID);
      }
   }

   public void chooseOneOpen(ArrayList<AbstractCard> choices) {
      this.rItem = null;
      this.codex = false;
      this.discovery = false;
      this.chooseOne = true;
      this.discoveryCard = null;
      this.draft = false;
      this.codexCard = null;
      this.header = TEXT[6];
      this.peekButton.hideInstantly();
      this.peekButton.show();
      this.bowlButton.hide();
      this.skippable = false;
      this.skipButton.hide();
      AbstractDungeon.topPanel.unhoverHitboxes();
      this.rewardGroup = choices;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(this.header);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard c : choices) {
         UnlockTracker.markCardAsSeen(c.cardID);
      }
   }

   public void draftOpen() {
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      this.draftCount++;
      this.rItem = null;
      this.codex = false;
      this.discovery = false;
      this.discoveryCard = null;
      this.chooseOne = false;
      this.draft = true;
      this.codexCard = null;
      this.header = TEXT[1];
      this.bowlButton.hide();
      this.skippable = false;
      this.skipButton.hide();
      AbstractDungeon.topPanel.unhoverHitboxes();
      ArrayList<AbstractCard> derp = new ArrayList<>();

      while (derp.size() != 3) {
         boolean dupe = false;
         AbstractCard tmp = AbstractDungeon.returnRandomCard();

         for (AbstractCard c : derp) {
            if (c.cardID.equals(tmp.cardID)) {
               dupe = true;
               break;
            }
         }

         if (!dupe) {
            derp.add(tmp.makeCopy());
         }
      }

      this.rewardGroup = derp;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(this.header);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard cx : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(cx.cardID);
      }
   }

   private void placeCards(float x, float y) {
      int maxPossibleStartingIndex = this.rewardGroup.size() - 4;
      int indexToStartAt = (int)(
         (maxPossibleStartingIndex + 1) * MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound, this.scrollX)
      );
      if (indexToStartAt > maxPossibleStartingIndex) {
         indexToStartAt = maxPossibleStartingIndex;
      }

      for (AbstractCard c : this.rewardGroup) {
         c.drawScale = 0.75F;
         c.targetDrawScale = 0.75F;
         if (this.rewardGroup.size() > 5) {
            if (this.rewardGroup.indexOf(c) < indexToStartAt) {
               c.current_x = -Settings.WIDTH * 0.25F;
            } else if (this.rewardGroup.indexOf(c) >= indexToStartAt + 4) {
               c.current_x = Settings.WIDTH * 1.25F;
            } else {
               c.current_x = x;
            }
         } else {
            c.current_x = x;
         }

         c.current_y = y;
      }
   }

   public void onClose() {
      if (AbstractDungeon.topPanel.twitch.isPresent()) {
         this.mayVote = false;
         this.updateVote();
      }

      if (Settings.isControllerMode) {
         InputHelper.moveCursorToNeutralPosition();
      }
   }

   public void reset() {
      this.draftCount = 0;
      this.codex = false;
      this.discovery = false;
      this.chooseOne = false;
      this.draft = false;
   }

   public void skippedCards() {
      this.recordMetrics("SKIP");
   }

   public void closeFromBowlButton() {
      this.recordMetrics("Singing Bowl");
   }

   private Optional<TwitchVoter> getVoter() {
      return TwitchPanel.getDefaultVoter();
   }

   private void updateVote() {
      if (this.getVoter().isPresent()) {
         TwitchVoter twitchVoter = this.getVoter().get();
         if (this.mayVote && twitchVoter.isVotingConnected() && !this.isVoting) {
            logger.info("Publishing Card Reward Vote");
            if (this.bowlButton.isHidden()) {
               this.isVoting = twitchVoter.initiateSimpleNumberVote(
                  Stream.concat(Stream.of(TEXT[0]), this.rewardGroup.stream().map(AbstractCard::toString)).toArray(String[]::new), this::completeVoting
               );
            } else {
               this.isVoting = twitchVoter.initiateSimpleNumberVote(
                  Stream.concat(Stream.builder().add(TEXT[0]).add(TEXT[2]).build(), this.rewardGroup.stream().map(AbstractCard::toString))
                     .toArray(String[]::new),
                  this::completeVoting
               );
            }
         } else if (this.isVoting && (!this.mayVote || !twitchVoter.isVotingConnected())) {
            twitchVoter.endVoting(true);
            this.isVoting = false;
         }
      }
   }

   @Override
   public void scrolledUsingBar(float newPercent) {
      this.scrollX = MathHelper.valueFromPercentBetween(this.scrollLowerBound, this.scrollUpperBound, newPercent);
      this.targetX = this.scrollX;
      this.updateBarPosition();
   }

   private void updateBarPosition() {
      float percent = MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound, this.scrollX);
      this.scrollBar.parentScrolledToPercent(percent);
   }

   private boolean shouldShowScrollBar() {
      return this.rewardGroup.size() > 5;
   }

   @Deprecated
   public void discoveryOpen() {
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      this.rItem = null;
      this.codex = false;
      this.discovery = true;
      this.chooseOne = false;
      this.discoveryCard = null;
      this.draft = false;
      this.codexCard = null;
      this.peekButton.hideInstantly();
      this.peekButton.show();
      this.bowlButton.hide();
      this.skipButton.hide();
      AbstractDungeon.topPanel.unhoverHitboxes();
      ArrayList<AbstractCard> derp = new ArrayList<>();

      while (derp.size() != 3) {
         boolean dupe = false;
         AbstractCard tmp = AbstractDungeon.returnTrulyRandomCardInCombat();

         for (AbstractCard c : derp) {
            if (c.cardID.equals(tmp.cardID)) {
               dupe = true;
               break;
            }
         }

         if (!dupe) {
            derp.add(tmp.makeCopy());
         }
      }

      this.rewardGroup = derp;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(TEXT[1]);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard cx : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(cx.cardID);
      }
   }

   @Deprecated
   public void chooseOneColorless() {
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      this.rItem = null;
      this.codex = false;
      this.discovery = true;
      this.chooseOne = false;
      this.discoveryCard = null;
      this.draft = false;
      this.codexCard = null;
      this.bowlButton.hide();
      this.skipButton.show();
      AbstractDungeon.topPanel.unhoverHitboxes();
      ArrayList<AbstractCard> derp = new ArrayList<>();

      while (derp.size() != 3) {
         boolean dupe = false;
         AbstractCard tmp = AbstractDungeon.returnColorlessCard();

         for (AbstractCard c : derp) {
            if (c.cardID.equals(tmp.cardID)) {
               dupe = true;
               break;
            }
         }

         if (!dupe) {
            derp.add(tmp.makeCopy());
         }
      }

      this.rewardGroup = derp;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(TEXT[1]);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard cx : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(cx.cardID);
      }
   }

   @Deprecated
   public void discoveryOpen(AbstractCard.CardType type) {
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      this.rItem = null;
      this.codex = false;
      this.discovery = true;
      this.chooseOne = false;
      this.discoveryCard = null;
      this.draft = false;
      this.codexCard = null;
      this.bowlButton.hide();
      this.skipButton.show();
      AbstractDungeon.topPanel.unhoverHitboxes();
      ArrayList<AbstractCard> derp = new ArrayList<>();

      while (derp.size() != 3) {
         boolean dupe = false;
         AbstractCard tmp = AbstractDungeon.returnTrulyRandomCardInCombat(type);

         for (AbstractCard c : derp) {
            if (c.cardID.equals(tmp.cardID)) {
               dupe = true;
               break;
            }
         }

         if (!dupe) {
            derp.add(tmp.makeCopy());
         }
      }

      this.rewardGroup = derp;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(TEXT[1]);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard cx : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(cx.cardID);
      }
   }

   @Deprecated
   public void carveRealityOpen(ArrayList<AbstractCard> cardsToPickBetween) {
      this.rItem = null;
      this.codex = false;
      this.discovery = true;
      this.chooseOne = false;
      this.discoveryCard = null;
      this.draft = false;
      this.codexCard = null;
      this.bowlButton.hide();
      this.skipButton.show();
      AbstractDungeon.topPanel.unhoverHitboxes();
      this.rewardGroup = cardsToPickBetween;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(TEXT[1]);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard c : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(c.cardID);
      }
   }

   @Deprecated
   public void foreignInfluenceOpen() {
      this.rItem = null;
      this.codex = false;
      this.discovery = true;
      this.chooseOne = false;
      this.discoveryCard = null;
      this.draft = false;
      this.codexCard = null;
      this.peekButton.hideInstantly();
      this.peekButton.show();
      this.bowlButton.hide();
      this.skipButton.show();
      AbstractDungeon.topPanel.unhoverHitboxes();
      ArrayList<AbstractCard> derp = new ArrayList<>();

      while (derp.size() != 3) {
         boolean dupe = false;
         int roll = AbstractDungeon.cardRandomRng.random(99);
         AbstractCard.CardRarity cardRarity;
         if (roll < 55) {
            cardRarity = AbstractCard.CardRarity.COMMON;
         } else if (roll < 85) {
            cardRarity = AbstractCard.CardRarity.UNCOMMON;
         } else {
            cardRarity = AbstractCard.CardRarity.RARE;
         }

         AbstractCard tmp = CardLibrary.getAnyColorCard(AbstractCard.CardType.ATTACK, cardRarity);

         for (AbstractCard c : derp) {
            if (c.cardID.equals(tmp.cardID)) {
               dupe = true;
               break;
            }
         }

         if (!dupe) {
            derp.add(tmp.makeCopy());
         }
      }

      this.rewardGroup = derp;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(TEXT[1]);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard cx : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(cx.cardID);
      }
   }

   @Deprecated
   public void codexOpen() {
      this.confirmButton.hideInstantly();
      this.touchCard = null;
      this.rItem = null;
      this.codex = true;
      this.discovery = false;
      this.discoveryCard = null;
      this.chooseOne = false;
      this.draft = false;
      this.codexCard = null;
      this.peekButton.hideInstantly();
      this.peekButton.show();
      this.bowlButton.hide();
      this.skipButton.show();
      AbstractDungeon.topPanel.unhoverHitboxes();
      ArrayList<AbstractCard> derp = new ArrayList<>();

      while (derp.size() != 3) {
         boolean dupe = false;
         AbstractCard tmp = AbstractDungeon.returnTrulyRandomCardInCombat();

         for (AbstractCard c : derp) {
            if (c.cardID.equals(tmp.cardID)) {
               dupe = true;
               break;
            }
         }

         if (!dupe) {
            derp.add(tmp.makeCopy());
         }
      }

      this.rewardGroup = derp;
      AbstractDungeon.isScreenUp = true;
      AbstractDungeon.screen = AbstractDungeon.CurrentScreen.CARD_REWARD;
      AbstractDungeon.dynamicBanner.appear(TEXT[1]);
      AbstractDungeon.overlayMenu.showBlackScreen();
      this.placeCards(Settings.WIDTH / 2.0F, CARD_TARGET_Y);

      for (AbstractCard cx : this.rewardGroup) {
         UnlockTracker.markCardAsSeen(cx.cardID);
      }
   }

   static {
      TEXT = uiStrings.TEXT;
      TwitchVoter.registerListener(new TwitchVoteListener() {
         @Override
         public void onTwitchAvailable() {
            AbstractDungeon.cardRewardScreen.updateVote();
         }

         @Override
         public void onTwitchUnavailable() {
            AbstractDungeon.cardRewardScreen.updateVote();
         }
      });
   }
}
