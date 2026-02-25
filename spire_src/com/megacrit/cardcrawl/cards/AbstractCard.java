package com.megacrit.cardcrawl.cards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ExhaustSpecificCardAction;
import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.OverlayMenu;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardHelper;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.GameDataStringBuilder;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.LocalizedStrings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.SingleCardViewPopup;
import com.megacrit.cardcrawl.stances.AbstractStance;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.cardManip.CardFlashVfx;
import com.megacrit.cardcrawl.vfx.cardManip.CardGlowBorder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractCard implements Comparable<AbstractCard> {
   private static final Logger logger = LogManager.getLogger(AbstractCard.class.getName());
   public AbstractCard.CardType type;
   public int cost;
   public int costForTurn;
   public int price;
   public int chargeCost = -1;
   public boolean isCostModified = false;
   public boolean isCostModifiedForTurn = false;
   public boolean retain = false;
   public boolean selfRetain = false;
   public boolean dontTriggerOnUseCard = false;
   public AbstractCard.CardRarity rarity;
   public AbstractCard.CardColor color;
   public boolean isInnate = false;
   public boolean isLocked = false;
   public boolean showEvokeValue = false;
   public int showEvokeOrbCount = 0;
   public ArrayList<String> keywords = new ArrayList<>();
   private static final int COMMON_CARD_PRICE = 50;
   private static final int UNCOMMON_CARD_PRICE = 75;
   private static final int RARE_CARD_PRICE = 150;
   protected boolean isUsed = false;
   public boolean upgraded = false;
   public int timesUpgraded = 0;
   public int misc = 0;
   public int energyOnUse;
   public boolean ignoreEnergyOnUse = false;
   public boolean isSeen = true;
   public boolean upgradedCost = false;
   public boolean upgradedDamage = false;
   public boolean upgradedBlock = false;
   public boolean upgradedMagicNumber = false;
   public UUID uuid;
   public boolean isSelected = false;
   public boolean exhaust = false;
   public boolean returnToHand = false;
   public boolean shuffleBackIntoDrawPile = false;
   public boolean isEthereal = false;
   public ArrayList<AbstractCard.CardTags> tags = new ArrayList<>();
   public int[] multiDamage;
   protected boolean isMultiDamage = false;
   public int baseDamage = -1;
   public int baseBlock = -1;
   public int baseMagicNumber = -1;
   public int baseHeal = -1;
   public int baseDraw = -1;
   public int baseDiscard = -1;
   public int damage = -1;
   public int block = -1;
   public int magicNumber = -1;
   public int heal = -1;
   public int draw = -1;
   public int discard = -1;
   public boolean isDamageModified = false;
   public boolean isBlockModified = false;
   public boolean isMagicNumberModified = false;
   protected DamageInfo.DamageType damageType;
   public DamageInfo.DamageType damageTypeForTurn;
   public AbstractCard.CardTarget target = AbstractCard.CardTarget.ENEMY;
   public boolean purgeOnUse = false;
   public boolean exhaustOnUseOnce = false;
   public boolean exhaustOnFire = false;
   public boolean freeToPlayOnce = false;
   public boolean isInAutoplay = false;
   private static TextureAtlas orbAtlas;
   private static TextureAtlas cardAtlas;
   private static TextureAtlas oldCardAtlas;
   public static TextureAtlas.AtlasRegion orb_red;
   public static TextureAtlas.AtlasRegion orb_green;
   public static TextureAtlas.AtlasRegion orb_blue;
   public static TextureAtlas.AtlasRegion orb_purple;
   public static TextureAtlas.AtlasRegion orb_card;
   public static TextureAtlas.AtlasRegion orb_potion;
   public static TextureAtlas.AtlasRegion orb_relic;
   public static TextureAtlas.AtlasRegion orb_special;
   public TextureAtlas.AtlasRegion portrait;
   public TextureAtlas.AtlasRegion jokePortrait;
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("SingleCardViewPopup");
   public static final String[] TEXT;
   public static float typeWidthAttack;
   public static float typeWidthSkill;
   public static float typeWidthPower;
   public static float typeWidthCurse;
   public static float typeWidthStatus;
   public static float typeOffsetAttack;
   public static float typeOffsetSkill;
   public static float typeOffsetPower;
   public static float typeOffsetCurse;
   public static float typeOffsetStatus;
   public AbstractGameEffect flashVfx;
   public String assetUrl;
   public boolean fadingOut = false;
   public float transparency = 1.0F;
   public float targetTransparency = 1.0F;
   private Color goldColor = Settings.GOLD_COLOR.cpy();
   private Color renderColor = Color.WHITE.cpy();
   private Color textColor = Settings.CREAM_COLOR.cpy();
   private Color typeColor = new Color(0.35F, 0.35F, 0.35F, 0.0F);
   public float targetAngle = 0.0F;
   private static final float NAME_OFFSET_Y = 175.0F;
   private static final float ENERGY_TEXT_OFFSET_X = -132.0F;
   private static final float ENERGY_TEXT_OFFSET_Y = 192.0F;
   private static final int W = 512;
   public float angle = 0.0F;
   private ArrayList<CardGlowBorder> glowList = new ArrayList<>();
   private float glowTimer = 0.0F;
   public boolean isGlowing = false;
   public static final float SMALL_SCALE = 0.7F;
   public static final int RAW_W = 300;
   public static final int RAW_H = 420;
   public static final float IMG_WIDTH = 300.0F * Settings.scale;
   public static final float IMG_HEIGHT = 420.0F * Settings.scale;
   public static final float IMG_WIDTH_S = 300.0F * Settings.scale * 0.7F;
   public static final float IMG_HEIGHT_S = 420.0F * Settings.scale * 0.7F;
   private static final float SHADOW_OFFSET_X = 18.0F * Settings.scale;
   private static final float SHADOW_OFFSET_Y = 14.0F * Settings.scale;
   public float current_x;
   public float current_y;
   public float target_x;
   public float target_y;
   protected Texture portraitImg = null;
   private boolean useSmallTitleFont = false;
   public float drawScale = 0.7F;
   public float targetDrawScale = 0.7F;
   private static final int PORTRAIT_WIDTH = 250;
   private static final int PORTRAIT_HEIGHT = 190;
   private static final float PORTRAIT_OFFSET_Y = 72.0F;
   private static final float LINE_SPACING = 1.45F;
   public boolean isFlipped = false;
   private boolean darken = false;
   private float darkTimer = 0.0F;
   private static final float DARKEN_TIME = 0.3F;
   public Hitbox hb = new Hitbox(IMG_WIDTH_S, IMG_HEIGHT_S);
   private static final float HB_W = 300.0F * Settings.scale;
   private static final float HB_H = 420.0F * Settings.scale;
   public float hoverTimer = 0.0F;
   private boolean renderTip = false;
   private boolean hovered = false;
   private float hoverDuration = 0.0F;
   private static final float HOVER_TIP_TIME = 0.2F;
   private static final GlyphLayout gl = new GlyphLayout();
   private static final StringBuilder sbuilder = new StringBuilder();
   private static final StringBuilder sbuilder2 = new StringBuilder();
   public AbstractCard cardsToPreview = null;
   protected static final float CARD_TIP_PAD = 16.0F;
   public float newGlowTimer = 0.0F;
   public String originalName;
   public String name;
   public String rawDescription;
   public String cardID;
   public ArrayList<DescriptionLine> description = new ArrayList<>();
   public String cantUseMessage;
   private static final float TYPE_OFFSET_Y = -1.0F;
   private static final float DESC_OFFSET_Y = Settings.BIG_TEXT_MODE ? IMG_HEIGHT * 0.24F : IMG_HEIGHT * 0.255F;
   private static final float DESC_OFFSET_Y2 = -6.0F;
   private static final float DESC_BOX_WIDTH = Settings.BIG_TEXT_MODE ? IMG_WIDTH * 0.95F : IMG_WIDTH * 0.79F;
   private static final float CN_DESC_BOX_WIDTH = Settings.BIG_TEXT_MODE ? IMG_WIDTH * 0.87F : IMG_WIDTH * 0.72F;
   private static final float TITLE_BOX_WIDTH = IMG_WIDTH * 0.6F;
   private static final float TITLE_BOX_WIDTH_NO_COST = IMG_WIDTH * 0.7F;
   private static final float CARD_ENERGY_IMG_WIDTH = 24.0F * Settings.scale;
   private static final float MAGIC_NUM_W = 20.0F * Settings.scale;
   private static final UIStrings cardRenderStrings = CardCrawlGame.languagePack.getUIString("AbstractCard");
   public static final String LOCKED_STRING;
   public static final String UNKNOWN_STRING;
   private Color bgColor;
   private Color backColor;
   private Color frameColor;
   private Color frameOutlineColor;
   private Color frameShadowColor;
   private Color imgFrameColor;
   private Color descBoxColor;
   private Color bannerColor;
   private Color tintColor;
   private static final Color ENERGY_COST_RESTRICTED_COLOR = new Color(1.0F, 0.3F, 0.3F, 1.0F);
   private static final Color ENERGY_COST_MODIFIED_COLOR = new Color(0.4F, 1.0F, 0.4F, 1.0F);
   private static final Color FRAME_SHADOW_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.25F);
   private static final Color IMG_FRAME_COLOR_COMMON = CardHelper.getColor(53, 58, 64);
   private static final Color IMG_FRAME_COLOR_UNCOMMON = CardHelper.getColor(119, 152, 161);
   private static final Color IMG_FRAME_COLOR_RARE = new Color(0.855F, 0.557F, 0.32F, 1.0F);
   private static final Color HOVER_IMG_COLOR = new Color(1.0F, 0.815F, 0.314F, 0.8F);
   private static final Color SELECTED_CARD_COLOR = new Color(0.5F, 0.9F, 0.9F, 1.0F);
   private static final Color BANNER_COLOR_COMMON = CardHelper.getColor(131, 129, 121);
   private static final Color BANNER_COLOR_UNCOMMON = CardHelper.getColor(142, 196, 213);
   private static final Color BANNER_COLOR_RARE = new Color(1.0F, 0.796F, 0.251F, 1.0F);
   private static final Color CURSE_BG_COLOR = CardHelper.getColor(29, 29, 29);
   private static final Color CURSE_TYPE_BACK_COLOR = new Color(0.23F, 0.23F, 0.23F, 1.0F);
   private static final Color CURSE_FRAME_COLOR = CardHelper.getColor(21, 2, 21);
   private static final Color CURSE_DESC_BOX_COLOR = CardHelper.getColor(52, 58, 64);
   private static final Color COLORLESS_BG_COLOR = new Color(0.15F, 0.15F, 0.15F, 1.0F);
   private static final Color COLORLESS_TYPE_BACK_COLOR = new Color(0.23F, 0.23F, 0.23F, 1.0F);
   private static final Color COLORLESS_FRAME_COLOR = new Color(0.48F, 0.48F, 0.48F, 1.0F);
   private static final Color COLORLESS_DESC_BOX_COLOR = new Color(0.351F, 0.363F, 0.3745F, 1.0F);
   private static final Color RED_BG_COLOR = CardHelper.getColor(50, 26, 26);
   private static final Color RED_TYPE_BACK_COLOR = CardHelper.getColor(91, 43, 32);
   private static final Color RED_FRAME_COLOR = CardHelper.getColor(121, 12, 28);
   private static final Color RED_RARE_OUTLINE_COLOR = new Color(1.0F, 0.75F, 0.43F, 1.0F);
   private static final Color RED_DESC_BOX_COLOR = CardHelper.getColor(53, 58, 64);
   private static final Color GREEN_BG_COLOR = CardHelper.getColor(19, 45, 40);
   private static final Color GREEN_TYPE_BACK_COLOR = CardHelper.getColor(32, 91, 43);
   private static final Color GREEN_FRAME_COLOR = CardHelper.getColor(52, 123, 8);
   private static final Color GREEN_RARE_OUTLINE_COLOR = new Color(1.0F, 0.75F, 0.43F, 1.0F);
   private static final Color GREEN_DESC_BOX_COLOR = CardHelper.getColor(53, 58, 64);
   private static final Color BLUE_BG_COLOR = CardHelper.getColor(19, 45, 40);
   private static final Color BLUE_TYPE_BACK_COLOR = CardHelper.getColor(32, 91, 43);
   private static final Color BLUE_FRAME_COLOR = CardHelper.getColor(52, 123, 8);
   private static final Color BLUE_RARE_OUTLINE_COLOR = new Color(1.0F, 0.75F, 0.43F, 1.0F);
   private static final Color BLUE_DESC_BOX_COLOR = CardHelper.getColor(53, 58, 64);
   protected static final Color BLUE_BORDER_GLOW_COLOR = new Color(0.2F, 0.9F, 1.0F, 0.25F);
   protected static final Color GREEN_BORDER_GLOW_COLOR = new Color(0.0F, 1.0F, 0.0F, 0.25F);
   protected static final Color GOLD_BORDER_GLOW_COLOR = Color.GOLD.cpy();
   public boolean inBottleFlame = false;
   public boolean inBottleLightning = false;
   public boolean inBottleTornado = false;
   public Color glowColor = BLUE_BORDER_GLOW_COLOR.cpy();

   public boolean isStarterStrike() {
      return this.hasTag(AbstractCard.CardTags.STRIKE) && this.rarity.equals(AbstractCard.CardRarity.BASIC);
   }

   public boolean isStarterDefend() {
      return this.hasTag(AbstractCard.CardTags.STARTER_DEFEND) && this.rarity.equals(AbstractCard.CardRarity.BASIC);
   }

   public AbstractCard(
      String id,
      String name,
      String imgUrl,
      int cost,
      String rawDescription,
      AbstractCard.CardType type,
      AbstractCard.CardColor color,
      AbstractCard.CardRarity rarity,
      AbstractCard.CardTarget target
   ) {
      this(id, name, imgUrl, cost, rawDescription, type, color, rarity, target, DamageInfo.DamageType.NORMAL);
   }

   public AbstractCard(
      String id,
      String name,
      String deprecatedJokeUrl,
      String imgUrl,
      int cost,
      String rawDescription,
      AbstractCard.CardType type,
      AbstractCard.CardColor color,
      AbstractCard.CardRarity rarity,
      AbstractCard.CardTarget target
   ) {
      this(id, name, imgUrl, cost, rawDescription, type, color, rarity, target, DamageInfo.DamageType.NORMAL);
   }

   public AbstractCard(
      String id,
      String name,
      String deprecatedJokeUrl,
      String imgUrl,
      int cost,
      String rawDescription,
      AbstractCard.CardType type,
      AbstractCard.CardColor color,
      AbstractCard.CardRarity rarity,
      AbstractCard.CardTarget target,
      DamageInfo.DamageType dType
   ) {
      this(id, name, imgUrl, cost, rawDescription, type, color, rarity, target, dType);
   }

   public AbstractCard(
      String id,
      String name,
      String imgUrl,
      int cost,
      String rawDescription,
      AbstractCard.CardType type,
      AbstractCard.CardColor color,
      AbstractCard.CardRarity rarity,
      AbstractCard.CardTarget target,
      DamageInfo.DamageType dType
   ) {
      this.originalName = name;
      this.name = name;
      this.cardID = id;
      this.assetUrl = imgUrl;
      this.portrait = cardAtlas.findRegion(imgUrl);
      this.jokePortrait = oldCardAtlas.findRegion(imgUrl);
      if (this.portrait == null) {
         if (this.jokePortrait != null) {
            this.portrait = this.jokePortrait;
         } else {
            this.portrait = cardAtlas.findRegion("status/beta");
         }
      }

      this.cost = cost;
      this.costForTurn = cost;
      this.rawDescription = rawDescription;
      this.type = type;
      this.color = color;
      this.rarity = rarity;
      this.target = target;
      this.damageType = dType;
      this.damageTypeForTurn = dType;
      this.createCardImage();
      if (name == null || rawDescription == null) {
         logger.info("Card initialized incorrecty");
      }

      this.initializeTitle();
      this.initializeDescription();
      this.updateTransparency();
      this.uuid = UUID.randomUUID();
   }

   public static void initialize() {
      long startTime = System.currentTimeMillis();
      cardAtlas = new TextureAtlas(Gdx.files.internal("cards/cards.atlas"));
      oldCardAtlas = new TextureAtlas(Gdx.files.internal("oldCards/cards.atlas"));
      orbAtlas = new TextureAtlas(Gdx.files.internal("orbs/orb.atlas"));
      orb_red = orbAtlas.findRegion("red");
      orb_green = orbAtlas.findRegion("green");
      orb_blue = orbAtlas.findRegion("blue");
      orb_purple = orbAtlas.findRegion("purple");
      orb_card = orbAtlas.findRegion("card");
      orb_potion = orbAtlas.findRegion("potion");
      orb_relic = orbAtlas.findRegion("relic");
      orb_special = orbAtlas.findRegion("special");
      logger.info("Card Image load time: " + (System.currentTimeMillis() - startTime) + "ms");
   }

   public static void initializeDynamicFrameWidths() {
      float d = 48.0F * Settings.scale;
      gl.setText(FontHelper.cardTypeFont, uiStrings.TEXT[0]);
      typeOffsetAttack = (gl.width - 48.0F * Settings.scale) / 2.0F;
      typeWidthAttack = (gl.width / d - 1.0F) * 2.0F + 1.0F;
      gl.setText(FontHelper.cardTypeFont, uiStrings.TEXT[1]);
      typeOffsetSkill = (gl.width - 48.0F * Settings.scale) / 2.0F;
      typeWidthSkill = (gl.width / d - 1.0F) * 2.0F + 1.0F;
      gl.setText(FontHelper.cardTypeFont, uiStrings.TEXT[2]);
      typeOffsetPower = (gl.width - 48.0F * Settings.scale) / 2.0F;
      typeWidthPower = (gl.width / d - 1.0F) * 2.0F + 1.0F;
      gl.setText(FontHelper.cardTypeFont, uiStrings.TEXT[3]);
      typeOffsetCurse = (gl.width - 48.0F * Settings.scale) / 2.0F;
      typeWidthCurse = (gl.width / d - 1.0F) * 2.0F + 1.0F;
      gl.setText(FontHelper.cardTypeFont, uiStrings.TEXT[7]);
      typeOffsetStatus = (gl.width - 48.0F * Settings.scale) / 2.0F;
      typeWidthStatus = (gl.width / d - 1.0F) * 2.0F + 1.0F;
   }

   protected void initializeTitle() {
      FontHelper.cardTitleFont.getData().setScale(1.0F);
      gl.setText(FontHelper.cardTitleFont, this.name, Color.WHITE, 0.0F, 1, false);
      if (this.cost <= 0 && this.cost != -1) {
         if (gl.width > TITLE_BOX_WIDTH_NO_COST) {
            this.useSmallTitleFont = true;
         }
      } else if (gl.width > TITLE_BOX_WIDTH) {
         this.useSmallTitleFont = true;
      }

      gl.reset();
   }

   public void initializeDescription() {
      this.keywords.clear();
      if (Settings.lineBreakViaCharacter) {
         this.initializeDescriptionCN();
      } else {
         this.description.clear();
         int numLines = 1;
         sbuilder.setLength(0);
         float currentWidth = 0.0F;

         for (String word : this.rawDescription.split(" ")) {
            boolean isKeyword = false;
            sbuilder2.setLength(0);
            sbuilder2.append(" ");
            if (word.length() > 0 && word.charAt(word.length() - 1) != ']' && !Character.isLetterOrDigit(word.charAt(word.length() - 1))) {
               sbuilder2.insert(0, word.charAt(word.length() - 1));
               word = word.substring(0, word.length() - 1);
            }

            String keywordTmp = word.toLowerCase();
            keywordTmp = this.dedupeKeyword(keywordTmp);
            if (GameDictionary.keywords.containsKey(keywordTmp)) {
               if (!this.keywords.contains(keywordTmp)) {
                  this.keywords.add(keywordTmp);
               }

               gl.reset();
               gl.setText(FontHelper.cardDescFont_N, sbuilder2);
               float tmp = gl.width;
               gl.setText(FontHelper.cardDescFont_N, word);
               gl.width += tmp;
               isKeyword = true;
            } else if (!word.isEmpty() && word.charAt(0) == '[') {
               gl.reset();
               gl.setText(FontHelper.cardDescFont_N, sbuilder2);
               gl.width = gl.width + CARD_ENERGY_IMG_WIDTH;
               switch (this.color) {
                  case RED:
                     if (!this.keywords.contains("[R]")) {
                        this.keywords.add("[R]");
                     }
                     break;
                  case GREEN:
                     if (!this.keywords.contains("[G]")) {
                        this.keywords.add("[G]");
                     }
                     break;
                  case BLUE:
                     if (!this.keywords.contains("[B]")) {
                        this.keywords.add("[B]");
                     }
                     break;
                  case PURPLE:
                     if (!this.keywords.contains("[W]")) {
                        this.keywords.add("[W]");
                     }
                     break;
                  case COLORLESS:
                     if (word.equals("[W]") && !this.keywords.contains("[W]")) {
                        this.keywords.add("[W]");
                     }
                     break;
                  default:
                     logger.info("ERROR: Tried to display an invalid energy type: " + this.color.name());
               }
            } else if (word.equals("!D") || word.equals("!B") || word.equals("!M")) {
               gl.setText(FontHelper.cardDescFont_N, word);
            } else if (word.equals("NL")) {
               gl.width = 0.0F;
               word = "";
               this.description.add(new DescriptionLine(sbuilder.toString().trim(), currentWidth));
               currentWidth = 0.0F;
               numLines++;
               sbuilder.setLength(0);
            } else {
               gl.setText(FontHelper.cardDescFont_N, word + sbuilder2);
            }

            if (currentWidth + gl.width > DESC_BOX_WIDTH) {
               this.description.add(new DescriptionLine(sbuilder.toString().trim(), currentWidth));
               numLines++;
               sbuilder.setLength(0);
               currentWidth = gl.width;
            } else {
               currentWidth += gl.width;
            }

            if (isKeyword) {
               sbuilder.append('*');
            }

            sbuilder.append(word).append((CharSequence)sbuilder2);
         }

         if (!sbuilder.toString().trim().isEmpty()) {
            this.description.add(new DescriptionLine(sbuilder.toString().trim(), currentWidth));
         }

         if (Settings.isDev && numLines > 5) {
            logger.info("WARNING: Card " + this.name + " has lots of text");
         }
      }
   }

   public void initializeDescriptionCN() {
      this.description.clear();
      int numLines = 1;
      sbuilder.setLength(0);
      float currentWidth = 0.0F;

      for (String word : this.rawDescription.split(" ")) {
         word = word.trim();
         if (Settings.manualLineBreak || Settings.manualAndAutoLineBreak || !word.contains("NL")) {
            if ((!word.equals("NL") || sbuilder.length() != 0) && !word.isEmpty()) {
               String keywordTmp = word.toLowerCase();
               keywordTmp = this.dedupeKeyword(keywordTmp);
               if (GameDictionary.keywords.containsKey(keywordTmp)) {
                  if (!this.keywords.contains(keywordTmp)) {
                     this.keywords.add(keywordTmp);
                  }

                  gl.setText(FontHelper.cardDescFont_N, word);
                  if (currentWidth + gl.width > CN_DESC_BOX_WIDTH) {
                     numLines++;
                     this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
                     sbuilder.setLength(0);
                     currentWidth = gl.width;
                     sbuilder.append(" *").append(word).append(" ");
                  } else {
                     sbuilder.append(" *").append(word).append(" ");
                     currentWidth += gl.width;
                  }
               } else if (!word.isEmpty() && word.charAt(0) == '[') {
                  switch (this.color) {
                     case RED:
                        if (!this.keywords.contains("[R]")) {
                           this.keywords.add("[R]");
                        }
                        break;
                     case GREEN:
                        if (!this.keywords.contains("[G]")) {
                           this.keywords.add("[G]");
                        }
                        break;
                     case BLUE:
                        if (!this.keywords.contains("[B]")) {
                           this.keywords.add("[B]");
                        }
                        break;
                     case PURPLE:
                        if (!this.keywords.contains("[W]")) {
                           this.keywords.add("[W]");
                        }
                        break;
                     case COLORLESS:
                        if (!this.keywords.contains("[W]")) {
                           this.keywords.add("[W]");
                        }
                        break;
                     default:
                        logger.info("ERROR: Tried to display an invalid energy type: " + this.color.name());
                  }

                  if (currentWidth + CARD_ENERGY_IMG_WIDTH > CN_DESC_BOX_WIDTH) {
                     numLines++;
                     this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
                     sbuilder.setLength(0);
                     currentWidth = CARD_ENERGY_IMG_WIDTH;
                     sbuilder.append(" ").append(word).append(" ");
                  } else {
                     sbuilder.append(" ").append(word).append(" ");
                     currentWidth += CARD_ENERGY_IMG_WIDTH;
                  }
               } else if (word.equals("!D!")) {
                  if (currentWidth + MAGIC_NUM_W > CN_DESC_BOX_WIDTH) {
                     numLines++;
                     this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
                     sbuilder.setLength(0);
                     currentWidth = MAGIC_NUM_W;
                     sbuilder.append(" D ");
                  } else {
                     sbuilder.append(" D ");
                     currentWidth += MAGIC_NUM_W;
                  }
               } else if (word.equals("!B!")) {
                  if (currentWidth + MAGIC_NUM_W > CN_DESC_BOX_WIDTH) {
                     numLines++;
                     this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
                     sbuilder.setLength(0);
                     currentWidth = MAGIC_NUM_W;
                     sbuilder.append(" ").append(word).append("! ");
                  } else {
                     sbuilder.append(" ").append(word).append("! ");
                     currentWidth += MAGIC_NUM_W;
                  }
               } else if (word.equals("!M!")) {
                  if (currentWidth + MAGIC_NUM_W > CN_DESC_BOX_WIDTH) {
                     numLines++;
                     this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
                     sbuilder.setLength(0);
                     currentWidth = MAGIC_NUM_W;
                     sbuilder.append(" ").append(word).append("! ");
                  } else {
                     sbuilder.append(" ").append(word).append("! ");
                     currentWidth += MAGIC_NUM_W;
                  }
               } else if ((Settings.manualLineBreak || Settings.manualAndAutoLineBreak) && word.equals("NL") && sbuilder.length() != 0) {
                  gl.width = 0.0F;
                  word = "";
                  this.description.add(new DescriptionLine(sbuilder.toString().trim(), currentWidth));
                  currentWidth = 0.0F;
                  numLines++;
                  sbuilder.setLength(0);
               } else if (word.charAt(0) == '*') {
                  gl.setText(FontHelper.cardDescFont_N, word.substring(1));
                  if (currentWidth + gl.width > CN_DESC_BOX_WIDTH) {
                     numLines++;
                     this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
                     sbuilder.setLength(0);
                     currentWidth = gl.width;
                     sbuilder.append(" ").append(word).append(" ");
                  } else {
                     sbuilder.append(" ").append(word).append(" ");
                     currentWidth += gl.width;
                  }
               } else {
                  word = word.trim();

                  for (char c : word.toCharArray()) {
                     gl.setText(FontHelper.cardDescFont_N, String.valueOf(c));
                     sbuilder.append(c);
                     if (!Settings.manualLineBreak) {
                        if (currentWidth + gl.width > CN_DESC_BOX_WIDTH) {
                           numLines++;
                           this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
                           sbuilder.setLength(0);
                           currentWidth = gl.width;
                        } else {
                           currentWidth += gl.width;
                        }
                     }
                  }
               }
            } else {
               currentWidth = 0.0F;
            }
         }
      }

      if (sbuilder.length() != 0) {
         this.description.add(new DescriptionLine(sbuilder.toString(), currentWidth));
      }

      int removeLine = -1;

      for (int i = 0; i < this.description.size(); i++) {
         if (this.description.get(i).text.equals(LocalizedStrings.PERIOD)) {
            StringBuilder var10000 = new StringBuilder();
            DescriptionLine var10002 = this.description.get(i - 1);
            var10002.text = var10000.append(var10002.text).append(LocalizedStrings.PERIOD).toString();
            removeLine = i;
         }
      }

      if (removeLine != -1) {
         this.description.remove(removeLine);
      }

      if (Settings.isDev && numLines > 5) {
         logger.info("WARNING: Card " + this.name + " has lots of text");
      }
   }

   public boolean hasTag(AbstractCard.CardTags tagToCheck) {
      return this.tags.contains(tagToCheck);
   }

   public boolean canUpgrade() {
      if (this.type == AbstractCard.CardType.CURSE) {
         return false;
      } else {
         return this.type == AbstractCard.CardType.STATUS ? false : !this.upgraded;
      }
   }

   public abstract void upgrade();

   public void displayUpgrades() {
      if (this.upgradedCost) {
         this.isCostModified = true;
      }

      if (this.upgradedDamage) {
         this.damage = this.baseDamage;
         this.isDamageModified = true;
      }

      if (this.upgradedBlock) {
         this.block = this.baseBlock;
         this.isBlockModified = true;
      }

      if (this.upgradedMagicNumber) {
         this.magicNumber = this.baseMagicNumber;
         this.isMagicNumberModified = true;
      }
   }

   protected void upgradeDamage(int amount) {
      this.baseDamage += amount;
      this.upgradedDamage = true;
   }

   protected void upgradeBlock(int amount) {
      this.baseBlock += amount;
      this.upgradedBlock = true;
   }

   protected void upgradeMagicNumber(int amount) {
      this.baseMagicNumber += amount;
      this.magicNumber = this.baseMagicNumber;
      this.upgradedMagicNumber = true;
   }

   protected void upgradeName() {
      this.timesUpgraded++;
      this.upgraded = true;
      this.name = this.name + "+";
      this.initializeTitle();
   }

   protected void upgradeBaseCost(int newBaseCost) {
      int diff = this.costForTurn - this.cost;
      this.cost = newBaseCost;
      if (this.costForTurn > 0) {
         this.costForTurn = this.cost + diff;
      }

      if (this.costForTurn < 0) {
         this.costForTurn = 0;
      }

      this.upgradedCost = true;
   }

   private String dedupeKeyword(String keyword) {
      String retVal = GameDictionary.parentWord.get(keyword);
      return retVal != null ? retVal : keyword;
   }

   public AbstractCard(AbstractCard c) {
      this.name = c.name;
      this.rawDescription = c.rawDescription;
      this.cost = c.cost;
      this.type = c.type;
   }

   private void createCardImage() {
      switch (this.color) {
         case RED:
            this.bgColor = RED_BG_COLOR.cpy();
            this.backColor = RED_TYPE_BACK_COLOR.cpy();
            this.frameColor = RED_FRAME_COLOR.cpy();
            this.frameOutlineColor = RED_RARE_OUTLINE_COLOR.cpy();
            this.descBoxColor = RED_DESC_BOX_COLOR.cpy();
            break;
         case GREEN:
            this.bgColor = GREEN_BG_COLOR.cpy();
            this.backColor = GREEN_TYPE_BACK_COLOR.cpy();
            this.frameColor = GREEN_FRAME_COLOR.cpy();
            this.frameOutlineColor = GREEN_RARE_OUTLINE_COLOR.cpy();
            this.descBoxColor = GREEN_DESC_BOX_COLOR.cpy();
            break;
         case BLUE:
            this.bgColor = BLUE_BG_COLOR.cpy();
            this.backColor = BLUE_TYPE_BACK_COLOR.cpy();
            this.frameColor = BLUE_FRAME_COLOR.cpy();
            this.frameOutlineColor = BLUE_RARE_OUTLINE_COLOR.cpy();
            this.descBoxColor = BLUE_DESC_BOX_COLOR.cpy();
         case PURPLE:
            this.bgColor = BLUE_BG_COLOR.cpy();
            this.backColor = BLUE_TYPE_BACK_COLOR.cpy();
            this.frameColor = BLUE_FRAME_COLOR.cpy();
            this.frameOutlineColor = BLUE_RARE_OUTLINE_COLOR.cpy();
            this.descBoxColor = BLUE_DESC_BOX_COLOR.cpy();
            break;
         case COLORLESS:
            this.bgColor = COLORLESS_BG_COLOR.cpy();
            this.backColor = COLORLESS_TYPE_BACK_COLOR.cpy();
            this.frameColor = COLORLESS_FRAME_COLOR.cpy();
            this.frameOutlineColor = Color.WHITE.cpy();
            this.descBoxColor = COLORLESS_DESC_BOX_COLOR.cpy();
            break;
         case CURSE:
            this.bgColor = CURSE_BG_COLOR.cpy();
            this.backColor = CURSE_TYPE_BACK_COLOR.cpy();
            this.frameColor = CURSE_FRAME_COLOR.cpy();
            this.descBoxColor = CURSE_DESC_BOX_COLOR.cpy();
            break;
         default:
            logger.info("ERROR: Card color was NOT set for " + this.name);
      }

      if (this.rarity == AbstractCard.CardRarity.COMMON || this.rarity == AbstractCard.CardRarity.BASIC || this.rarity == AbstractCard.CardRarity.CURSE) {
         this.bannerColor = BANNER_COLOR_COMMON.cpy();
         this.imgFrameColor = IMG_FRAME_COLOR_COMMON.cpy();
      } else if (this.rarity == AbstractCard.CardRarity.UNCOMMON) {
         this.bannerColor = BANNER_COLOR_UNCOMMON.cpy();
         this.imgFrameColor = IMG_FRAME_COLOR_UNCOMMON.cpy();
      } else {
         this.bannerColor = BANNER_COLOR_RARE.cpy();
         this.imgFrameColor = IMG_FRAME_COLOR_RARE.cpy();
      }

      this.tintColor = CardHelper.getColor(43, 37, 65);
      this.tintColor.a = 0.0F;
      this.frameShadowColor = FRAME_SHADOW_COLOR.cpy();
   }

   public AbstractCard makeSameInstanceOf() {
      AbstractCard card = this.makeStatEquivalentCopy();
      card.uuid = this.uuid;
      return card;
   }

   public AbstractCard makeStatEquivalentCopy() {
      AbstractCard card = this.makeCopy();

      for (int i = 0; i < this.timesUpgraded; i++) {
         card.upgrade();
      }

      card.name = this.name;
      card.target = this.target;
      card.upgraded = this.upgraded;
      card.timesUpgraded = this.timesUpgraded;
      card.baseDamage = this.baseDamage;
      card.baseBlock = this.baseBlock;
      card.baseMagicNumber = this.baseMagicNumber;
      card.cost = this.cost;
      card.costForTurn = this.costForTurn;
      card.isCostModified = this.isCostModified;
      card.isCostModifiedForTurn = this.isCostModifiedForTurn;
      card.inBottleLightning = this.inBottleLightning;
      card.inBottleFlame = this.inBottleFlame;
      card.inBottleTornado = this.inBottleTornado;
      card.isSeen = this.isSeen;
      card.isLocked = this.isLocked;
      card.misc = this.misc;
      card.freeToPlayOnce = this.freeToPlayOnce;
      return card;
   }

   public void onRemoveFromMasterDeck() {
   }

   public boolean cardPlayable(AbstractMonster m) {
      if ((this.target != AbstractCard.CardTarget.ENEMY && this.target != AbstractCard.CardTarget.SELF_AND_ENEMY || m == null || !m.isDying)
         && !AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         return true;
      } else {
         this.cantUseMessage = null;
         return false;
      }
   }

   public boolean hasEnoughEnergy() {
      if (AbstractDungeon.actionManager.turnHasEnded) {
         this.cantUseMessage = TEXT[9];
         return false;
      } else {
         for (AbstractPower p : AbstractDungeon.player.powers) {
            if (!p.canPlayCard(this)) {
               this.cantUseMessage = TEXT[13];
               return false;
            }
         }

         if (AbstractDungeon.player.hasPower("Entangled") && this.type == AbstractCard.CardType.ATTACK) {
            this.cantUseMessage = TEXT[10];
            return false;
         } else {
            for (AbstractRelic r : AbstractDungeon.player.relics) {
               if (!r.canPlay(this)) {
                  return false;
               }
            }

            for (AbstractBlight b : AbstractDungeon.player.blights) {
               if (!b.canPlay(this)) {
                  return false;
               }
            }

            for (AbstractCard c : AbstractDungeon.player.hand.group) {
               if (!c.canPlay(this)) {
                  return false;
               }
            }

            if (EnergyPanel.totalCount < this.costForTurn && !this.freeToPlay() && !this.isInAutoplay) {
               this.cantUseMessage = TEXT[11];
               return false;
            } else {
               return true;
            }
         }
      }
   }

   public void tookDamage() {
   }

   public void didDiscard() {
   }

   public void switchedStance() {
   }

   @Deprecated
   protected void useBlueCandle(AbstractPlayer p) {
   }

   @Deprecated
   protected void useMedicalKit(AbstractPlayer p) {
   }

   public boolean canPlay(AbstractCard card) {
      return true;
   }

   public boolean canUse(AbstractPlayer p, AbstractMonster m) {
      if (this.type == AbstractCard.CardType.STATUS && this.costForTurn < -1 && !AbstractDungeon.player.hasRelic("Medical Kit")) {
         return false;
      } else {
         return this.type == AbstractCard.CardType.CURSE && this.costForTurn < -1 && !AbstractDungeon.player.hasRelic("Blue Candle")
            ? false
            : this.cardPlayable(m) && this.hasEnoughEnergy();
      }
   }

   public abstract void use(AbstractPlayer var1, AbstractMonster var2);

   public void update() {
      this.updateFlashVfx();
      if (this.hoverTimer != 0.0F) {
         this.hoverTimer = this.hoverTimer - Gdx.graphics.getDeltaTime();
         if (this.hoverTimer < 0.0F) {
            this.hoverTimer = 0.0F;
         }
      }

      if (AbstractDungeon.player != null && AbstractDungeon.player.isDraggingCard && this == AbstractDungeon.player.hoveredCard) {
         this.current_x = MathHelper.cardLerpSnap(this.current_x, this.target_x);
         this.current_y = MathHelper.cardLerpSnap(this.current_y, this.target_y);
         if (AbstractDungeon.player.hasRelic("Necronomicon")) {
            if (this.cost >= 2 && this.type == AbstractCard.CardType.ATTACK && AbstractDungeon.player.getRelic("Necronomicon").checkTrigger()) {
               AbstractDungeon.player.getRelic("Necronomicon").beginLongPulse();
            } else {
               AbstractDungeon.player.getRelic("Necronomicon").stopPulse();
            }
         }
      }

      if (Settings.FAST_MODE) {
         this.current_x = MathHelper.cardLerpSnap(this.current_x, this.target_x);
         this.current_y = MathHelper.cardLerpSnap(this.current_y, this.target_y);
      }

      this.current_x = MathHelper.cardLerpSnap(this.current_x, this.target_x);
      this.current_y = MathHelper.cardLerpSnap(this.current_y, this.target_y);
      this.hb.move(this.current_x, this.current_y);
      this.hb.resize(HB_W * this.drawScale, HB_H * this.drawScale);
      if (this.hb.clickStarted && this.hb.hovered) {
         this.drawScale = MathHelper.cardScaleLerpSnap(this.drawScale, this.targetDrawScale * 0.9F);
         this.drawScale = MathHelper.cardScaleLerpSnap(this.drawScale, this.targetDrawScale * 0.9F);
      } else {
         this.drawScale = MathHelper.cardScaleLerpSnap(this.drawScale, this.targetDrawScale);
      }

      if (this.angle != this.targetAngle) {
         this.angle = MathHelper.angleLerpSnap(this.angle, this.targetAngle);
      }

      this.updateTransparency();
      this.updateColor();
   }

   private void updateFlashVfx() {
      if (this.flashVfx != null) {
         this.flashVfx.update();
         if (this.flashVfx.isDone) {
            this.flashVfx = null;
         }
      }
   }

   private void updateGlow() {
      if (this.isGlowing) {
         this.glowTimer = this.glowTimer - Gdx.graphics.getDeltaTime();
         if (this.glowTimer < 0.0F) {
            this.glowList.add(new CardGlowBorder(this, this.glowColor));
            this.glowTimer = 0.3F;
         }
      }

      Iterator<CardGlowBorder> i = this.glowList.iterator();

      while (i.hasNext()) {
         CardGlowBorder e = i.next();
         e.update();
         if (e.isDone) {
            i.remove();
         }
      }
   }

   public boolean isHoveredInHand(float scale) {
      if (this.hoverTimer > 0.0F) {
         return false;
      } else {
         int x = InputHelper.mX;
         int y = InputHelper.mY;
         return x > this.current_x - IMG_WIDTH * scale / 2.0F
            && x < this.current_x + IMG_WIDTH * scale / 2.0F
            && y > this.current_y - IMG_HEIGHT * scale / 2.0F
            && y < this.current_y + IMG_HEIGHT * scale / 2.0F;
      }
   }

   private boolean isOnScreen() {
      return !(this.current_y < -200.0F * Settings.scale) && !(this.current_y > Settings.HEIGHT + 200.0F * Settings.scale);
   }

   public void render(SpriteBatch sb) {
      if (!Settings.hideCards) {
         this.render(sb, false);
      }
   }

   public void renderHoverShadow(SpriteBatch sb) {
      if (!Settings.hideCards) {
         this.renderHelper(sb, Settings.TWO_THIRDS_TRANSPARENT_BLACK_COLOR, ImageMaster.CARD_SUPER_SHADOW, this.current_x, this.current_y, 1.15F);
      }
   }

   public void renderInLibrary(SpriteBatch sb) {
      if (this.isOnScreen()) {
         if (SingleCardViewPopup.isViewingUpgrade && this.isSeen && !this.isLocked) {
            AbstractCard copy = this.makeCopy();
            copy.current_x = this.current_x;
            copy.current_y = this.current_y;
            copy.drawScale = this.drawScale;
            copy.upgrade();
            copy.displayUpgrades();
            copy.render(sb);
         } else {
            this.updateGlow();
            this.renderGlow(sb);
            this.renderImage(sb, this.hovered, false);
            this.renderType(sb);
            this.renderTitle(sb);
            if (Settings.lineBreakViaCharacter) {
               this.renderDescriptionCN(sb);
            } else {
               this.renderDescription(sb);
            }

            this.renderTint(sb);
            this.renderEnergy(sb);
            this.hb.render(sb);
         }
      }
   }

   public void render(SpriteBatch sb, boolean selected) {
      if (!Settings.hideCards) {
         if (this.flashVfx != null) {
            this.flashVfx.render(sb);
         }

         this.renderCard(sb, this.hovered, selected);
         this.hb.render(sb);
      }
   }

   public void renderUpgradePreview(SpriteBatch sb) {
      this.upgraded = true;
      this.name = this.originalName + "+";
      this.initializeTitle();
      this.renderCard(sb, this.hovered, false);
      this.name = this.originalName;
      this.initializeTitle();
      this.upgraded = false;
      this.resetAttributes();
   }

   public void renderWithSelections(SpriteBatch sb) {
      this.renderCard(sb, false, true);
   }

   private void renderCard(SpriteBatch sb, boolean hovered, boolean selected) {
      if (!Settings.hideCards) {
         if (!this.isOnScreen()) {
            return;
         }

         if (!this.isFlipped) {
            this.updateGlow();
            this.renderGlow(sb);
            this.renderImage(sb, hovered, selected);
            this.renderTitle(sb);
            this.renderType(sb);
            if (Settings.lineBreakViaCharacter) {
               this.renderDescriptionCN(sb);
            } else {
               this.renderDescription(sb);
            }

            this.renderTint(sb);
            this.renderEnergy(sb);
         } else {
            this.renderBack(sb, hovered, selected);
            this.hb.render(sb);
         }
      }
   }

   private void renderTint(SpriteBatch sb) {
      if (!Settings.hideCards) {
         TextureAtlas.AtlasRegion cardBgImg = this.getCardBgAtlas();
         if (cardBgImg != null) {
            this.renderHelper(sb, this.tintColor, cardBgImg, this.current_x, this.current_y);
         } else {
            this.renderHelper(sb, this.tintColor, this.getCardBg(), this.current_x - 256.0F, this.current_y - 256.0F);
         }
      }
   }

   public void renderOuterGlow(SpriteBatch sb) {
      if (AbstractDungeon.player != null && Settings.hideCards) {
         this.renderHelper(
            sb, AbstractDungeon.player.getCardRenderColor(), this.getCardBgAtlas(), this.current_x, this.current_y, 1.0F + this.tintColor.a / 5.0F
         );
      }
   }

   public Texture getCardBg() {
      int i = 10;
      if (i < 5) {
         System.out.println("Add special logic here");
      }

      return null;
   }

   public TextureAtlas.AtlasRegion getCardBgAtlas() {
      switch (this.type) {
         case ATTACK:
            return ImageMaster.CARD_ATTACK_BG_SILHOUETTE;
         case CURSE:
         case STATUS:
         case SKILL:
            return ImageMaster.CARD_SKILL_BG_SILHOUETTE;
         case POWER:
            return ImageMaster.CARD_POWER_BG_SILHOUETTE;
         default:
            return null;
      }
   }

   private void renderGlow(SpriteBatch sb) {
      if (!Settings.hideCards) {
         this.renderMainBorder(sb);

         for (AbstractGameEffect e : this.glowList) {
            e.render(sb);
         }

         sb.setBlendFunction(770, 771);
      }
   }

   public void beginGlowing() {
      this.isGlowing = true;
   }

   public void stopGlowing() {
      this.isGlowing = false;

      for (CardGlowBorder e : this.glowList) {
         e.duration /= 5.0F;
      }
   }

   private void renderMainBorder(SpriteBatch sb) {
      if (this.isGlowing) {
         sb.setBlendFunction(770, 1);
         TextureAtlas.AtlasRegion img;
         switch (this.type) {
            case ATTACK:
               img = ImageMaster.CARD_ATTACK_BG_SILHOUETTE;
               break;
            case POWER:
               img = ImageMaster.CARD_POWER_BG_SILHOUETTE;
               break;
            default:
               img = ImageMaster.CARD_SKILL_BG_SILHOUETTE;
         }

         if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
            sb.setColor(this.glowColor);
         } else {
            sb.setColor(GREEN_BORDER_GLOW_COLOR);
         }

         sb.draw(
            img,
            this.current_x + img.offsetX - img.originalWidth / 2.0F,
            this.current_y + img.offsetY - img.originalWidth / 2.0F,
            img.originalWidth / 2.0F - img.offsetX,
            img.originalWidth / 2.0F - img.offsetY,
            img.packedWidth,
            img.packedHeight,
            this.drawScale * Settings.scale * 1.04F,
            this.drawScale * Settings.scale * 1.03F,
            this.angle
         );
      }
   }

   private void renderHelper(SpriteBatch sb, Color color, TextureAtlas.AtlasRegion img, float drawX, float drawY) {
      sb.setColor(color);
      sb.draw(
         img,
         drawX + img.offsetX - img.originalWidth / 2.0F,
         drawY + img.offsetY - img.originalHeight / 2.0F,
         img.originalWidth / 2.0F - img.offsetX,
         img.originalHeight / 2.0F - img.offsetY,
         img.packedWidth,
         img.packedHeight,
         this.drawScale * Settings.scale,
         this.drawScale * Settings.scale,
         this.angle
      );
   }

   private void renderHelper(SpriteBatch sb, Color color, TextureAtlas.AtlasRegion img, float drawX, float drawY, float scale) {
      sb.setColor(color);
      sb.draw(
         img,
         drawX + img.offsetX - img.originalWidth / 2.0F,
         drawY + img.offsetY - img.originalHeight / 2.0F,
         img.originalWidth / 2.0F - img.offsetX,
         img.originalHeight / 2.0F - img.offsetY,
         img.packedWidth,
         img.packedHeight,
         this.drawScale * Settings.scale * scale,
         this.drawScale * Settings.scale * scale,
         this.angle
      );
   }

   private void renderHelper(SpriteBatch sb, Color color, Texture img, float drawX, float drawY) {
      sb.setColor(color);
      sb.draw(
         img,
         drawX + 256.0F,
         drawY + 256.0F,
         256.0F,
         256.0F,
         512.0F,
         512.0F,
         this.drawScale * Settings.scale,
         this.drawScale * Settings.scale,
         this.angle,
         0,
         0,
         512,
         512,
         false,
         false
      );
   }

   private void renderHelper(SpriteBatch sb, Color color, Texture img, float drawX, float drawY, float scale) {
      sb.setColor(color);
      sb.draw(
         img,
         drawX,
         drawY,
         256.0F,
         256.0F,
         512.0F,
         512.0F,
         this.drawScale * Settings.scale * scale,
         this.drawScale * Settings.scale * scale,
         this.angle,
         0,
         0,
         512,
         512,
         false,
         false
      );
   }

   public void renderSmallEnergy(SpriteBatch sb, TextureAtlas.AtlasRegion region, float x, float y) {
      sb.setColor(this.renderColor);
      sb.draw(
         region.getTexture(),
         this.current_x + x * Settings.scale * this.drawScale + region.offsetX * Settings.scale,
         this.current_y + y * Settings.scale * this.drawScale + region.offsetY * Settings.scale,
         0.0F,
         0.0F,
         region.packedWidth,
         region.packedHeight,
         this.drawScale * Settings.scale,
         this.drawScale * Settings.scale,
         0.0F,
         region.getRegionX(),
         region.getRegionY(),
         region.getRegionWidth(),
         region.getRegionHeight(),
         false,
         false
      );
   }

   private void renderImage(SpriteBatch sb, boolean hovered, boolean selected) {
      if (AbstractDungeon.player != null) {
         if (selected) {
            this.renderHelper(sb, Color.SKY, this.getCardBgAtlas(), this.current_x, this.current_y, 1.03F);
         }

         this.renderHelper(
            sb,
            this.frameShadowColor,
            this.getCardBgAtlas(),
            this.current_x + SHADOW_OFFSET_X * this.drawScale,
            this.current_y - SHADOW_OFFSET_Y * this.drawScale
         );
         if (AbstractDungeon.player.hoveredCard != this
            || (!AbstractDungeon.player.isDraggingCard || !AbstractDungeon.player.isHoveringDropZone) && !AbstractDungeon.player.inSingleTargetMode) {
            if (selected) {
               this.renderHelper(sb, SELECTED_CARD_COLOR, this.getCardBgAtlas(), this.current_x, this.current_y);
            }
         } else {
            this.renderHelper(sb, HOVER_IMG_COLOR, this.getCardBgAtlas(), this.current_x, this.current_y);
         }
      }

      this.renderCardBg(sb, this.current_x, this.current_y);
      if (!UnlockTracker.betaCardPref.getBoolean(this.cardID, false) && !Settings.PLAYTESTER_ART_MODE) {
         this.renderPortrait(sb);
      } else {
         this.renderJokePortrait(sb);
      }

      this.renderPortraitFrame(sb, this.current_x, this.current_y);
      this.renderBannerImage(sb, this.current_x, this.current_y);
   }

   private void renderCardBg(SpriteBatch sb, float x, float y) {
      switch (this.type) {
         case ATTACK:
            this.renderAttackBg(sb, x, y);
            break;
         case CURSE:
            this.renderSkillBg(sb, x, y);
            break;
         case STATUS:
            this.renderSkillBg(sb, x, y);
            break;
         case SKILL:
            this.renderSkillBg(sb, x, y);
            break;
         case POWER:
            this.renderPowerBg(sb, x, y);
      }
   }

   private void renderAttackBg(SpriteBatch sb, float x, float y) {
      switch (this.color) {
         case RED:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_ATTACK_BG_RED, x, y);
            break;
         case GREEN:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_ATTACK_BG_GREEN, x, y);
            break;
         case BLUE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_ATTACK_BG_BLUE, x, y);
            break;
         case PURPLE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_ATTACK_BG_PURPLE, x, y);
            break;
         case COLORLESS:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_ATTACK_BG_GRAY, x, y);
            break;
         case CURSE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_BLACK, x, y);
            break;
         default:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_BLACK, x, y);
      }
   }

   private void renderSkillBg(SpriteBatch sb, float x, float y) {
      switch (this.color) {
         case RED:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_RED, x, y);
            break;
         case GREEN:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_GREEN, x, y);
            break;
         case BLUE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_BLUE, x, y);
            break;
         case PURPLE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_PURPLE, x, y);
            break;
         case COLORLESS:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_GRAY, x, y);
            break;
         case CURSE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_BLACK, x, y);
            break;
         default:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_BLACK, x, y);
      }
   }

   private void renderPowerBg(SpriteBatch sb, float x, float y) {
      switch (this.color) {
         case RED:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_POWER_BG_RED, x, y);
            break;
         case GREEN:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_POWER_BG_GREEN, x, y);
            break;
         case BLUE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_POWER_BG_BLUE, x, y);
            break;
         case PURPLE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_POWER_BG_PURPLE, x, y);
            break;
         case COLORLESS:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_POWER_BG_GRAY, x, y);
            break;
         case CURSE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_BLACK, x, y);
            break;
         default:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_SKILL_BG_BLACK, x, y);
      }
   }

   private void renderPortraitFrame(SpriteBatch sb, float x, float y) {
      float tWidth = 0.0F;
      float tOffset = 0.0F;
      switch (this.type) {
         case ATTACK:
            this.renderAttackPortrait(sb, x, y);
            tWidth = typeWidthAttack;
            tOffset = typeOffsetAttack;
            break;
         case CURSE:
            this.renderSkillPortrait(sb, x, y);
            tWidth = typeWidthCurse;
            tOffset = typeOffsetCurse;
            break;
         case STATUS:
            this.renderSkillPortrait(sb, x, y);
            tWidth = typeWidthStatus;
            tOffset = typeOffsetStatus;
            break;
         case SKILL:
            this.renderSkillPortrait(sb, x, y);
            tWidth = typeWidthSkill;
            tOffset = typeOffsetSkill;
            break;
         case POWER:
            this.renderPowerPortrait(sb, x, y);
            tWidth = typeWidthPower;
            tOffset = typeOffsetPower;
      }

      this.renderDynamicFrame(sb, x, y, tOffset, tWidth);
   }

   private void renderAttackPortrait(SpriteBatch sb, float x, float y) {
      switch (this.rarity) {
         case BASIC:
         case CURSE:
         case SPECIAL:
         case COMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_ATTACK_COMMON, x, y);
            return;
         case UNCOMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_ATTACK_UNCOMMON, x, y);
            return;
         case RARE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_ATTACK_RARE, x, y);
            return;
      }
   }

   private void renderDynamicFrame(SpriteBatch sb, float x, float y, float typeOffset, float typeWidth) {
      if (!(typeWidth <= 1.1F)) {
         switch (this.rarity) {
            case BASIC:
            case CURSE:
            case SPECIAL:
            case COMMON:
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_COMMON_FRAME_MID, x, y, 0.0F, typeWidth);
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_COMMON_FRAME_LEFT, x, y, -typeOffset, 1.0F);
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_COMMON_FRAME_RIGHT, x, y, typeOffset, 1.0F);
               break;
            case UNCOMMON:
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_UNCOMMON_FRAME_MID, x, y, 0.0F, typeWidth);
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_UNCOMMON_FRAME_LEFT, x, y, -typeOffset, 1.0F);
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_UNCOMMON_FRAME_RIGHT, x, y, typeOffset, 1.0F);
               break;
            case RARE:
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_RARE_FRAME_MID, x, y, 0.0F, typeWidth);
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_RARE_FRAME_LEFT, x, y, -typeOffset, 1.0F);
               this.dynamicFrameRenderHelper(sb, ImageMaster.CARD_RARE_FRAME_RIGHT, x, y, typeOffset, 1.0F);
         }
      }
   }

   private void dynamicFrameRenderHelper(SpriteBatch sb, TextureAtlas.AtlasRegion img, float x, float y, float xOffset, float xScale) {
      sb.draw(
         img,
         x + img.offsetX - img.originalWidth / 2.0F + xOffset * this.drawScale,
         y + img.offsetY - img.originalHeight / 2.0F,
         img.originalWidth / 2.0F - img.offsetX,
         img.originalHeight / 2.0F - img.offsetY,
         img.packedWidth,
         img.packedHeight,
         this.drawScale * Settings.scale * xScale,
         this.drawScale * Settings.scale,
         this.angle
      );
   }

   private void dynamicFrameRenderHelper(SpriteBatch sb, Texture img, float x, float y, float xOffset, float xScale) {
      sb.draw(
         img,
         x + xOffset * this.drawScale,
         y,
         256.0F,
         256.0F,
         512.0F,
         512.0F,
         this.drawScale * Settings.scale * xScale,
         this.drawScale * Settings.scale,
         this.angle,
         0,
         0,
         512,
         512,
         false,
         false
      );
   }

   private void renderSkillPortrait(SpriteBatch sb, float x, float y) {
      switch (this.rarity) {
         case BASIC:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_SKILL_COMMON, x, y);
            return;
         case CURSE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_SKILL_COMMON, x, y);
            return;
         case SPECIAL:
         default:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_SKILL_COMMON, x, y);
            return;
         case COMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_SKILL_COMMON, x, y);
            return;
         case UNCOMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_SKILL_UNCOMMON, x, y);
            return;
         case RARE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_SKILL_RARE, x, y);
      }
   }

   private void renderPowerPortrait(SpriteBatch sb, float x, float y) {
      switch (this.rarity) {
         case BASIC:
         case CURSE:
         case SPECIAL:
         case COMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_POWER_COMMON, x, y);
            break;
         case UNCOMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_POWER_UNCOMMON, x, y);
            break;
         case RARE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_FRAME_POWER_RARE, x, y);
      }
   }

   private void renderBannerImage(SpriteBatch sb, float drawX, float drawY) {
      switch (this.rarity) {
         case BASIC:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BANNER_COMMON, drawX, drawY);
            return;
         case CURSE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BANNER_COMMON, drawX, drawY);
            return;
         case SPECIAL:
         default:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BANNER_COMMON, drawX, drawY);
            return;
         case COMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BANNER_COMMON, drawX, drawY);
            return;
         case UNCOMMON:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BANNER_UNCOMMON, drawX, drawY);
            return;
         case RARE:
            this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BANNER_RARE, drawX, drawY);
      }
   }

   private void renderBack(SpriteBatch sb, boolean hovered, boolean selected) {
      this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BACK, this.current_x, this.current_y);
   }

   private void renderPortrait(SpriteBatch sb) {
      float drawX = this.current_x - 125.0F;
      float drawY = this.current_y - 95.0F;
      Texture img = null;
      if (this.portraitImg != null) {
         img = this.portraitImg;
      }

      if (!this.isLocked) {
         if (this.portrait != null) {
            drawX = this.current_x - this.portrait.packedWidth / 2.0F;
            drawY = this.current_y - this.portrait.packedHeight / 2.0F;
            sb.setColor(this.renderColor);
            sb.draw(
               this.portrait,
               drawX,
               drawY + 72.0F,
               this.portrait.packedWidth / 2.0F,
               this.portrait.packedHeight / 2.0F - 72.0F,
               this.portrait.packedWidth,
               this.portrait.packedHeight,
               this.drawScale * Settings.scale,
               this.drawScale * Settings.scale,
               this.angle
            );
         } else if (img != null) {
            sb.setColor(this.renderColor);
            sb.draw(
               img,
               drawX,
               drawY + 72.0F,
               125.0F,
               23.0F,
               250.0F,
               190.0F,
               this.drawScale * Settings.scale,
               this.drawScale * Settings.scale,
               this.angle,
               0,
               0,
               250,
               190,
               false,
               false
            );
         }
      } else {
         sb.draw(
            this.portraitImg,
            drawX,
            drawY + 72.0F,
            125.0F,
            23.0F,
            250.0F,
            190.0F,
            this.drawScale * Settings.scale,
            this.drawScale * Settings.scale,
            this.angle,
            0,
            0,
            250,
            190,
            false,
            false
         );
      }
   }

   private void renderJokePortrait(SpriteBatch sb) {
      float drawX = this.current_x - 125.0F;
      float drawY = this.current_y - 95.0F;
      Texture img = null;
      if (this.portraitImg != null) {
         img = this.portraitImg;
      }

      if (!this.isLocked) {
         if (this.jokePortrait != null) {
            drawX = this.current_x - this.portrait.packedWidth / 2.0F;
            drawY = this.current_y - this.portrait.packedHeight / 2.0F;
            sb.setColor(this.renderColor);
            sb.draw(
               this.jokePortrait,
               drawX,
               drawY + 72.0F,
               this.jokePortrait.packedWidth / 2.0F,
               this.jokePortrait.packedHeight / 2.0F - 72.0F,
               this.jokePortrait.packedWidth,
               this.jokePortrait.packedHeight,
               this.drawScale * Settings.scale,
               this.drawScale * Settings.scale,
               this.angle
            );
         } else if (img != null) {
            sb.setColor(this.renderColor);
            sb.draw(
               img,
               drawX,
               drawY + 72.0F,
               125.0F,
               23.0F,
               250.0F,
               190.0F,
               this.drawScale * Settings.scale,
               this.drawScale * Settings.scale,
               this.angle,
               0,
               0,
               250,
               190,
               false,
               false
            );
         }
      } else {
         sb.draw(
            this.portraitImg,
            drawX,
            drawY + 72.0F,
            125.0F,
            23.0F,
            250.0F,
            190.0F,
            this.drawScale * Settings.scale,
            this.drawScale * Settings.scale,
            this.angle,
            0,
            0,
            250,
            190,
            false,
            false
         );
      }
   }

   private void renderDescription(SpriteBatch sb) {
      if (this.isSeen && !this.isLocked) {
         BitmapFont font = this.getDescFont();
         float draw_y = this.current_y - IMG_HEIGHT * this.drawScale / 2.0F + DESC_OFFSET_Y * this.drawScale;
         draw_y += this.description.size() * font.getCapHeight() * 0.775F - font.getCapHeight() * 0.375F;
         float spacing = 1.45F * -font.getCapHeight() / Settings.scale / this.drawScale;

         for (int i = 0; i < this.description.size(); i++) {
            float start_x = this.current_x - this.description.get(i).width * this.drawScale / 2.0F;

            for (String tmp : this.description.get(i).getCachedTokenizedText()) {
               if (tmp.length() > 0 && tmp.charAt(0) == '*') {
                  tmp = tmp.substring(1);
                  String punctuation = "";
                  if (tmp.length() > 1 && tmp.charAt(tmp.length() - 2) != '+' && !Character.isLetter(tmp.charAt(tmp.length() - 2))) {
                     punctuation = punctuation + tmp.charAt(tmp.length() - 2);
                     tmp = tmp.substring(0, tmp.length() - 2);
                     punctuation = punctuation + ' ';
                  }

                  gl.setText(font, tmp);
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     tmp,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + gl.width / 2.0F,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.goldColor
                  );
                  start_x = Math.round(start_x + gl.width);
                  gl.setText(font, punctuation);
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     punctuation,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + gl.width / 2.0F,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  gl.setText(font, punctuation);
                  start_x += gl.width;
               } else if (tmp.length() > 0 && tmp.charAt(0) == '!') {
                  if (tmp.length() == 4) {
                     start_x += this.renderDynamicVariable(tmp.charAt(1), start_x, draw_y, i, font, sb, null);
                  } else if (tmp.length() == 5) {
                     start_x += this.renderDynamicVariable(tmp.charAt(1), start_x, draw_y, i, font, sb, tmp.charAt(3));
                  }
               } else if (tmp.equals("[R] ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_red,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[R]. ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale / Settings.scale;
                  this.renderSmallEnergy(
                     sb,
                     orb_red,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     LocalizedStrings.PERIOD,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + CARD_ENERGY_IMG_WIDTH * this.drawScale,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  start_x += gl.width;
                  gl.setText(font, LocalizedStrings.PERIOD);
                  start_x += gl.width;
               } else if (tmp.equals("[G] ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_green,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[G]. ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_green,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     LocalizedStrings.PERIOD,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + CARD_ENERGY_IMG_WIDTH * this.drawScale,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[B] ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_blue,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[B]. ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_blue,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     LocalizedStrings.PERIOD,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + CARD_ENERGY_IMG_WIDTH * this.drawScale,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[W] ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_purple,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[W]. ")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_purple,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     LocalizedStrings.PERIOD,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + CARD_ENERGY_IMG_WIDTH * this.drawScale,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  start_x += gl.width;
               } else {
                  gl.setText(font, tmp);
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     tmp,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + gl.width / 2.0F,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  start_x += gl.width;
               }
            }
         }

         font.getData().setScale(1.0F);
      } else {
         FontHelper.menuBannerFont.getData().setScale(this.drawScale * 1.25F);
         FontHelper.renderRotatedText(
            sb,
            FontHelper.menuBannerFont,
            "? ? ?",
            this.current_x,
            this.current_y,
            0.0F,
            -200.0F * Settings.scale * this.drawScale / 2.0F,
            this.angle,
            true,
            this.textColor
         );
         FontHelper.menuBannerFont.getData().setScale(1.0F);
      }
   }

   private String getDynamicValue(char key) {
      switch (key) {
         case 'B':
            if (this.isBlockModified) {
               if (this.block >= this.baseBlock) {
                  return "[#7fff00]" + Integer.toString(this.block) + "[]";
               }

               return "[#ff6563]" + Integer.toString(this.block) + "[]";
            }

            return Integer.toString(this.baseBlock);
         case 'D':
            if (this.isDamageModified) {
               if (this.damage >= this.baseDamage) {
                  return "[#7fff00]" + Integer.toString(this.damage) + "[]";
               }

               return "[#ff6563]" + Integer.toString(this.damage) + "[]";
            }

            return Integer.toString(this.baseDamage);
         case 'M':
            if (this.isMagicNumberModified) {
               if (this.magicNumber >= this.baseMagicNumber) {
                  return "[#7fff00]" + Integer.toString(this.magicNumber) + "[]";
               }

               return "[#ff6563]" + Integer.toString(this.magicNumber) + "[]";
            }

            return Integer.toString(this.baseMagicNumber);
         default:
            logger.info("KEY: " + key);
            return Integer.toString(-99);
      }
   }

   private void renderDescriptionCN(SpriteBatch sb) {
      if (this.isSeen && !this.isLocked) {
         BitmapFont font = this.getDescFont();
         float draw_y = this.current_y - IMG_HEIGHT * this.drawScale / 2.0F + DESC_OFFSET_Y * this.drawScale;
         draw_y += this.description.size() * font.getCapHeight() * 0.775F - font.getCapHeight() * 0.375F;
         float spacing = 1.45F * -font.getCapHeight() / Settings.scale / this.drawScale;

         for (int i = 0; i < this.description.size(); i++) {
            float start_x = 0.0F;
            if (Settings.leftAlignCards) {
               start_x = this.current_x - DESC_BOX_WIDTH * this.drawScale / 2.0F + 2.0F * Settings.scale;
            } else {
               start_x = this.current_x - this.description.get(i).width * this.drawScale / 2.0F - 14.0F * Settings.scale;
            }

            for (String tmp : this.description.get(i).getCachedTokenizedTextCN()) {
               String updateTmp = null;

               for (int j = 0; j < tmp.length(); j++) {
                  if (tmp.charAt(j) == 'D' || tmp.charAt(j) == 'B' && !tmp.contains("[B]") || tmp.charAt(j) == 'M') {
                     updateTmp = tmp.substring(0, j);
                     updateTmp = updateTmp + this.getDynamicValue(tmp.charAt(j));
                     updateTmp = updateTmp + tmp.substring(j + 1);
                     break;
                  }
               }

               if (updateTmp != null) {
                  tmp = updateTmp;
               }

               for (int jx = 0; jx < tmp.length(); jx++) {
                  if (tmp.charAt(jx) == 'D' || tmp.charAt(jx) == 'B' && !tmp.contains("[B]") || tmp.charAt(jx) == 'M') {
                     updateTmp = tmp.substring(0, jx);
                     updateTmp = updateTmp + this.getDynamicValue(tmp.charAt(jx));
                     updateTmp = updateTmp + tmp.substring(jx + 1);
                     break;
                  }
               }

               if (updateTmp != null) {
                  tmp = updateTmp;
               }

               if (tmp.length() > 0 && tmp.charAt(0) == '*') {
                  tmp = tmp.substring(1);
                  String punctuation = "";
                  if (tmp.length() > 1 && tmp.charAt(tmp.length() - 2) != '+' && !Character.isLetter(tmp.charAt(tmp.length() - 2))) {
                     punctuation = punctuation + tmp.charAt(tmp.length() - 2);
                     tmp = tmp.substring(0, tmp.length() - 2);
                     punctuation = punctuation + ' ';
                  }

                  gl.setText(font, tmp);
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     tmp,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + gl.width / 2.0F,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.goldColor
                  );
                  start_x = Math.round(start_x + gl.width);
                  gl.setText(font, punctuation);
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     punctuation,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + gl.width / 2.0F,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  gl.setText(font, punctuation);
                  start_x += gl.width;
               } else if (tmp.equals("[R]")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_red,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[G]")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_green,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[B]")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_blue,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else if (tmp.equals("[W]")) {
                  gl.width = CARD_ENERGY_IMG_WIDTH * this.drawScale;
                  this.renderSmallEnergy(
                     sb,
                     orb_purple,
                     (start_x - this.current_x) / Settings.scale / this.drawScale,
                     -100.0F - ((this.description.size() - 4.0F) / 2.0F - i + 1.0F) * spacing
                  );
                  start_x += gl.width;
               } else {
                  gl.setText(font, tmp);
                  FontHelper.renderRotatedText(
                     sb,
                     font,
                     tmp,
                     this.current_x,
                     this.current_y,
                     start_x - this.current_x + gl.width / 2.0F,
                     i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
                     this.angle,
                     true,
                     this.textColor
                  );
                  start_x += gl.width;
               }
            }
         }

         font.getData().setScale(1.0F);
      } else {
         FontHelper.menuBannerFont.getData().setScale(this.drawScale * 1.25F);
         FontHelper.renderRotatedText(
            sb,
            FontHelper.menuBannerFont,
            "? ? ?",
            this.current_x,
            this.current_y,
            0.0F,
            -200.0F * Settings.scale * this.drawScale / 2.0F,
            this.angle,
            true,
            this.textColor
         );
         FontHelper.menuBannerFont.getData().setScale(1.0F);
      }
   }

   private float renderDynamicVariable(char key, float start_x, float draw_y, int i, BitmapFont font, SpriteBatch sb, Character end) {
      sbuilder.setLength(0);
      Color c = null;
      int num = 0;
      switch (key) {
         case 'B':
            if (this.isBlockModified) {
               num = this.block;
               if (this.block >= this.baseBlock) {
                  c = Settings.GREEN_TEXT_COLOR;
               } else {
                  c = Settings.RED_TEXT_COLOR;
               }
            } else {
               c = this.textColor;
               num = this.baseBlock;
            }
            break;
         case 'D':
            if (this.isDamageModified) {
               num = this.damage;
               if (this.damage >= this.baseDamage) {
                  c = Settings.GREEN_TEXT_COLOR;
               } else {
                  c = Settings.RED_TEXT_COLOR;
               }
            } else {
               c = this.textColor;
               num = this.baseDamage;
            }
            break;
         case 'M':
            if (this.isMagicNumberModified) {
               num = this.magicNumber;
               if (this.magicNumber >= this.baseMagicNumber) {
                  c = Settings.GREEN_TEXT_COLOR;
               } else {
                  c = Settings.RED_TEXT_COLOR;
               }
            } else {
               c = this.textColor;
               num = this.baseMagicNumber;
            }
      }

      sbuilder.append(Integer.toString(num));
      gl.setText(font, sbuilder);
      FontHelper.renderRotatedText(
         sb,
         font,
         sbuilder.toString(),
         this.current_x,
         this.current_y,
         start_x - this.current_x + gl.width / 2.0F,
         i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
         this.angle,
         true,
         c
      );
      if (end != null) {
         FontHelper.renderRotatedText(
            sb,
            font,
            Character.toString(end),
            this.current_x,
            this.current_y,
            start_x - this.current_x + gl.width + 4.0F * Settings.scale,
            i * 1.45F * -font.getCapHeight() + draw_y - this.current_y + -6.0F,
            0.0F,
            true,
            Settings.CREAM_COLOR
         );
         sbuilder.append(end);
      }

      sbuilder.append(' ');
      gl.setText(font, sbuilder);
      return gl.width;
   }

   private BitmapFont getDescFont() {
      BitmapFont font = null;
      if (this.angle == 0.0F && this.drawScale == 1.0F) {
         font = FontHelper.cardDescFont_N;
      } else {
         font = FontHelper.cardDescFont_L;
      }

      font.getData().setScale(this.drawScale);
      return font;
   }

   private void renderTitle(SpriteBatch sb) {
      if (this.isLocked) {
         FontHelper.cardTitleFont.getData().setScale(this.drawScale);
         FontHelper.renderRotatedText(
            sb,
            FontHelper.cardTitleFont,
            LOCKED_STRING,
            this.current_x,
            this.current_y,
            0.0F,
            175.0F * this.drawScale * Settings.scale,
            this.angle,
            false,
            this.renderColor
         );
      } else if (!this.isSeen) {
         FontHelper.cardTitleFont.getData().setScale(this.drawScale);
         FontHelper.renderRotatedText(
            sb,
            FontHelper.cardTitleFont,
            UNKNOWN_STRING,
            this.current_x,
            this.current_y,
            0.0F,
            175.0F * this.drawScale * Settings.scale,
            this.angle,
            false,
            this.renderColor
         );
      } else {
         if (!this.useSmallTitleFont) {
            FontHelper.cardTitleFont.getData().setScale(this.drawScale);
         } else {
            FontHelper.cardTitleFont.getData().setScale(this.drawScale * 0.85F);
         }

         if (this.upgraded) {
            Color color = Settings.GREEN_TEXT_COLOR.cpy();
            color.a = this.renderColor.a;
            FontHelper.renderRotatedText(
               sb,
               FontHelper.cardTitleFont,
               this.name,
               this.current_x,
               this.current_y,
               0.0F,
               175.0F * this.drawScale * Settings.scale,
               this.angle,
               false,
               color
            );
         } else {
            FontHelper.renderRotatedText(
               sb,
               FontHelper.cardTitleFont,
               this.name,
               this.current_x,
               this.current_y,
               0.0F,
               175.0F * this.drawScale * Settings.scale,
               this.angle,
               false,
               this.renderColor
            );
         }
      }
   }

   private void renderType(SpriteBatch sb) {
      String text;
      switch (this.type) {
         case ATTACK:
            text = TEXT[0];
            break;
         case CURSE:
            text = TEXT[3];
            break;
         case STATUS:
            text = TEXT[7];
            break;
         case SKILL:
            text = TEXT[1];
            break;
         case POWER:
            text = TEXT[2];
            break;
         default:
            text = TEXT[5];
      }

      BitmapFont font = FontHelper.cardTypeFont;
      font.getData().setScale(this.drawScale);
      this.typeColor.a = this.renderColor.a;
      FontHelper.renderRotatedText(
         sb,
         font,
         text,
         this.current_x,
         this.current_y - 22.0F * this.drawScale * Settings.scale,
         0.0F,
         -1.0F * this.drawScale * Settings.scale,
         this.angle,
         false,
         this.typeColor
      );
   }

   public static int getPrice(AbstractCard.CardRarity rarity) {
      switch (rarity) {
         case BASIC:
            logger.info("ERROR: WHY WE SELLIN' BASIC");
            return 9999;
         case CURSE:
         default:
            logger.info("No rarity on this card?");
            return 0;
         case SPECIAL:
            logger.info("ERROR: WHY WE SELLIN' SPECIAL");
            return 9999;
         case COMMON:
            return 50;
         case UNCOMMON:
            return 75;
         case RARE:
            return 150;
      }
   }

   private void renderEnergy(SpriteBatch sb) {
      if (this.cost > -2 && !this.darken && !this.isLocked && this.isSeen) {
         switch (this.color) {
            case RED:
               this.renderHelper(sb, this.renderColor, ImageMaster.CARD_RED_ORB, this.current_x, this.current_y);
               break;
            case GREEN:
               this.renderHelper(sb, this.renderColor, ImageMaster.CARD_GREEN_ORB, this.current_x, this.current_y);
               break;
            case BLUE:
               this.renderHelper(sb, this.renderColor, ImageMaster.CARD_BLUE_ORB, this.current_x, this.current_y);
               break;
            case PURPLE:
               this.renderHelper(sb, this.renderColor, ImageMaster.CARD_PURPLE_ORB, this.current_x, this.current_y);
               break;
            case COLORLESS:
               this.renderHelper(sb, this.renderColor, ImageMaster.CARD_COLORLESS_ORB, this.current_x, this.current_y);
            default:
               this.renderHelper(sb, this.renderColor, ImageMaster.CARD_COLORLESS_ORB, this.current_x, this.current_y);
         }

         Color costColor = Color.WHITE.cpy();
         if (AbstractDungeon.player != null && AbstractDungeon.player.hand.contains(this) && !this.hasEnoughEnergy()) {
            costColor = ENERGY_COST_RESTRICTED_COLOR;
         } else if (this.isCostModified || this.isCostModifiedForTurn || this.freeToPlay()) {
            costColor = ENERGY_COST_MODIFIED_COLOR;
         }

         costColor.a = this.transparency;
         String text = this.getCost();
         BitmapFont font = this.getEnergyFont();
         if ((this.type != AbstractCard.CardType.STATUS || this.cardID.equals("Slimed"))
            && (this.color != AbstractCard.CardColor.CURSE || this.cardID.equals("Pride"))) {
            FontHelper.renderRotatedText(
               sb,
               font,
               text,
               this.current_x,
               this.current_y,
               -132.0F * this.drawScale * Settings.scale,
               192.0F * this.drawScale * Settings.scale,
               this.angle,
               false,
               costColor
            );
         }
      }
   }

   public void updateCost(int amt) {
      if ((this.color != AbstractCard.CardColor.CURSE || this.cardID.equals("Pride"))
         && (this.type != AbstractCard.CardType.STATUS || this.cardID.equals("Slimed"))) {
         int tmpCost = this.cost;
         int diff = this.cost - this.costForTurn;
         tmpCost += amt;
         if (tmpCost < 0) {
            tmpCost = 0;
         }

         if (tmpCost != this.cost) {
            this.isCostModified = true;
            this.cost = tmpCost;
            this.costForTurn = this.cost - diff;
            if (this.costForTurn < 0) {
               this.costForTurn = 0;
            }
         }
      } else {
         logger.info("Curses/Statuses cannot have their costs modified");
      }
   }

   public void setCostForTurn(int amt) {
      if (this.costForTurn >= 0) {
         this.costForTurn = amt;
         if (this.costForTurn < 0) {
            this.costForTurn = 0;
         }

         if (this.costForTurn != this.cost) {
            this.isCostModifiedForTurn = true;
         }
      }
   }

   public void modifyCostForCombat(int amt) {
      if (this.costForTurn > 0) {
         this.costForTurn += amt;
         if (this.costForTurn < 0) {
            this.costForTurn = 0;
         }

         if (this.cost != this.costForTurn) {
            this.isCostModified = true;
         }

         this.cost = this.costForTurn;
      } else if (this.cost >= 0) {
         this.cost += amt;
         if (this.cost < 0) {
            this.cost = 0;
         }

         this.costForTurn = 0;
         if (this.cost != this.costForTurn) {
            this.isCostModified = true;
         }
      }
   }

   public void resetAttributes() {
      this.block = this.baseBlock;
      this.isBlockModified = false;
      this.damage = this.baseDamage;
      this.isDamageModified = false;
      this.magicNumber = this.baseMagicNumber;
      this.isMagicNumberModified = false;
      this.damageTypeForTurn = this.damageType;
      this.costForTurn = this.cost;
      this.isCostModifiedForTurn = false;
   }

   private String getCost() {
      if (this.cost == -1) {
         return "X";
      } else {
         return this.freeToPlay() ? "0" : Integer.toString(this.costForTurn);
      }
   }

   public boolean freeToPlay() {
      return this.freeToPlayOnce
         ? true
         : AbstractDungeon.player != null
            && AbstractDungeon.currMapNode != null
            && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT
            && AbstractDungeon.player.hasPower("FreeAttackPower")
            && this.type == AbstractCard.CardType.ATTACK;
   }

   private BitmapFont getEnergyFont() {
      FontHelper.cardEnergyFont_L.getData().setScale(this.drawScale);
      return FontHelper.cardEnergyFont_L;
   }

   public void hover() {
      if (!this.hovered) {
         this.hovered = true;
         this.drawScale = 1.0F;
         this.targetDrawScale = 1.0F;
      }
   }

   public void unhover() {
      if (this.hovered) {
         this.hovered = false;
         this.hoverDuration = 0.0F;
         this.renderTip = false;
         this.targetDrawScale = 0.75F;
      }
   }

   public void updateHoverLogic() {
      this.hb.update();
      if (this.hb.hovered) {
         this.hover();
         this.hoverDuration = this.hoverDuration + Gdx.graphics.getDeltaTime();
         if (this.hoverDuration > 0.2F && !Settings.hideCards) {
            this.renderTip = true;
         }
      } else {
         this.unhover();
      }
   }

   public void untip() {
      this.hoverDuration = 0.0F;
      this.renderTip = false;
   }

   public void moveToDiscardPile() {
      this.target_x = CardGroup.DISCARD_PILE_X;
      if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
         this.target_y = 0.0F;
      } else {
         this.target_y = 0.0F - OverlayMenu.HAND_HIDE_Y;
      }
   }

   public void teleportToDiscardPile() {
      this.current_x = CardGroup.DISCARD_PILE_X;
      this.target_x = this.current_x;
      if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
         this.current_y = 0.0F;
      } else {
         this.current_y = 0.0F - OverlayMenu.HAND_HIDE_Y;
      }

      this.target_y = this.current_y;
      this.onMoveToDiscard();
   }

   public void onMoveToDiscard() {
   }

   public void renderCardTip(SpriteBatch sb) {
      if (!Settings.hideCards && this.renderTip) {
         if (AbstractDungeon.player != null && AbstractDungeon.player.isDraggingCard && !Settings.isTouchScreen) {
            return;
         }

         if (this.isLocked) {
            ArrayList<String> locked = new ArrayList<>();
            locked.add(0, "locked");
            TipHelper.renderTipForCard(this, sb, locked);
            return;
         }

         if (!this.isSeen) {
            ArrayList<String> unseen = new ArrayList<>();
            unseen.add(0, "unseen");
            TipHelper.renderTipForCard(this, sb, unseen);
            return;
         }

         if (SingleCardViewPopup.isViewingUpgrade && this.isSeen && !this.isLocked) {
            AbstractCard copy = this.makeCopy();
            copy.current_x = this.current_x;
            copy.current_y = this.current_y;
            copy.drawScale = this.drawScale;
            copy.upgrade();
            TipHelper.renderTipForCard(copy, sb, copy.keywords);
         } else {
            TipHelper.renderTipForCard(this, sb, this.keywords);
         }

         if (this.cardsToPreview != null) {
            this.renderCardPreview(sb);
         }
      }
   }

   public void renderCardPreviewInSingleView(SpriteBatch sb) {
      this.cardsToPreview.current_x = 1435.0F * Settings.scale;
      this.cardsToPreview.current_y = 795.0F * Settings.scale;
      this.cardsToPreview.drawScale = 0.8F;
      this.cardsToPreview.render(sb);
   }

   public void renderCardPreview(SpriteBatch sb) {
      if (AbstractDungeon.player == null || !AbstractDungeon.player.isDraggingCard) {
         float tmpScale = this.drawScale * 0.8F;
         if (this.current_x > Settings.WIDTH * 0.75F) {
            this.cardsToPreview.current_x = this.current_x + (IMG_WIDTH / 2.0F + IMG_WIDTH / 2.0F * 0.8F + 16.0F) * this.drawScale;
         } else {
            this.cardsToPreview.current_x = this.current_x - (IMG_WIDTH / 2.0F + IMG_WIDTH / 2.0F * 0.8F + 16.0F) * this.drawScale;
         }

         this.cardsToPreview.current_y = this.current_y + (IMG_HEIGHT / 2.0F - IMG_HEIGHT / 2.0F * 0.8F) * this.drawScale;
         this.cardsToPreview.drawScale = tmpScale;
         this.cardsToPreview.render(sb);
      }
   }

   public void triggerWhenDrawn() {
   }

   public void triggerWhenCopied() {
   }

   public void triggerOnEndOfPlayerTurn() {
      if (this.isEthereal) {
         this.addToTop(new ExhaustSpecificCardAction(this, AbstractDungeon.player.hand));
      }
   }

   public void triggerOnEndOfTurnForPlayingCard() {
   }

   public void triggerOnOtherCardPlayed(AbstractCard c) {
   }

   public void triggerOnGainEnergy(int e, boolean dueToCard) {
   }

   public void triggerOnManualDiscard() {
   }

   public void triggerOnCardPlayed(AbstractCard cardPlayed) {
   }

   public void triggerOnScry() {
   }

   public void triggerExhaustedCardsOnStanceChange(AbstractStance newStance) {
   }

   public void triggerAtStartOfTurn() {
   }

   public void onPlayCard(AbstractCard c, AbstractMonster m) {
   }

   public void atTurnStart() {
   }

   public void atTurnStartPreDraw() {
   }

   public void onChoseThisOption() {
   }

   public void onRetained() {
   }

   public void triggerOnExhaust() {
   }

   public void applyPowers() {
      this.applyPowersToBlock();
      AbstractPlayer player = AbstractDungeon.player;
      this.isDamageModified = false;
      if (!this.isMultiDamage) {
         float tmp = this.baseDamage;

         for (AbstractRelic r : player.relics) {
            tmp = r.atDamageModify(tmp, this);
            if (this.baseDamage != (int)tmp) {
               this.isDamageModified = true;
            }
         }

         for (AbstractPower p : player.powers) {
            tmp = p.atDamageGive(tmp, this.damageTypeForTurn, this);
         }

         tmp = player.stance.atDamageGive(tmp, this.damageTypeForTurn, this);
         if (this.baseDamage != (int)tmp) {
            this.isDamageModified = true;
         }

         for (AbstractPower p : player.powers) {
            tmp = p.atDamageFinalGive(tmp, this.damageTypeForTurn, this);
         }

         if (tmp < 0.0F) {
            tmp = 0.0F;
         }

         if (this.baseDamage != MathUtils.floor(tmp)) {
            this.isDamageModified = true;
         }

         this.damage = MathUtils.floor(tmp);
      } else {
         ArrayList<AbstractMonster> m = AbstractDungeon.getCurrRoom().monsters.monsters;
         float[] tmp = new float[m.size()];

         for (int i = 0; i < tmp.length; i++) {
            tmp[i] = this.baseDamage;
         }

         for (int i = 0; i < tmp.length; i++) {
            for (AbstractRelic rx : player.relics) {
               tmp[i] = rx.atDamageModify(tmp[i], this);
               if (this.baseDamage != (int)tmp[i]) {
                  this.isDamageModified = true;
               }
            }

            for (AbstractPower p : player.powers) {
               tmp[i] = p.atDamageGive(tmp[i], this.damageTypeForTurn, this);
            }

            tmp[i] = player.stance.atDamageGive(tmp[i], this.damageTypeForTurn, this);
            if (this.baseDamage != (int)tmp[i]) {
               this.isDamageModified = true;
            }
         }

         for (int i = 0; i < tmp.length; i++) {
            for (AbstractPower p : player.powers) {
               tmp[i] = p.atDamageFinalGive(tmp[i], this.damageTypeForTurn, this);
            }
         }

         for (int i = 0; i < tmp.length; i++) {
            if (tmp[i] < 0.0F) {
               tmp[i] = 0.0F;
            }
         }

         this.multiDamage = new int[tmp.length];

         for (int ix = 0; ix < tmp.length; ix++) {
            if (this.baseDamage != (int)tmp[ix]) {
               this.isDamageModified = true;
            }

            this.multiDamage[ix] = MathUtils.floor(tmp[ix]);
         }

         this.damage = this.multiDamage[0];
      }
   }

   protected void applyPowersToBlock() {
      this.isBlockModified = false;
      float tmp = this.baseBlock;

      for (AbstractPower p : AbstractDungeon.player.powers) {
         tmp = p.modifyBlock(tmp, this);
      }

      for (AbstractPower p : AbstractDungeon.player.powers) {
         tmp = p.modifyBlockLast(tmp);
      }

      if (this.baseBlock != MathUtils.floor(tmp)) {
         this.isBlockModified = true;
      }

      if (tmp < 0.0F) {
         tmp = 0.0F;
      }

      this.block = MathUtils.floor(tmp);
   }

   public void calculateDamageDisplay(AbstractMonster mo) {
      this.calculateCardDamage(mo);
   }

   public void calculateCardDamage(AbstractMonster mo) {
      this.applyPowersToBlock();
      AbstractPlayer player = AbstractDungeon.player;
      this.isDamageModified = false;
      if (!this.isMultiDamage && mo != null) {
         float tmp = this.baseDamage;

         for (AbstractRelic r : player.relics) {
            tmp = r.atDamageModify(tmp, this);
            if (this.baseDamage != (int)tmp) {
               this.isDamageModified = true;
            }
         }

         for (AbstractPower p : player.powers) {
            tmp = p.atDamageGive(tmp, this.damageTypeForTurn, this);
         }

         tmp = player.stance.atDamageGive(tmp, this.damageTypeForTurn, this);
         if (this.baseDamage != (int)tmp) {
            this.isDamageModified = true;
         }

         for (AbstractPower p : mo.powers) {
            tmp = p.atDamageReceive(tmp, this.damageTypeForTurn, this);
         }

         for (AbstractPower p : player.powers) {
            tmp = p.atDamageFinalGive(tmp, this.damageTypeForTurn, this);
         }

         for (AbstractPower p : mo.powers) {
            tmp = p.atDamageFinalReceive(tmp, this.damageTypeForTurn, this);
         }

         if (tmp < 0.0F) {
            tmp = 0.0F;
         }

         if (this.baseDamage != MathUtils.floor(tmp)) {
            this.isDamageModified = true;
         }

         this.damage = MathUtils.floor(tmp);
      } else {
         ArrayList<AbstractMonster> m = AbstractDungeon.getCurrRoom().monsters.monsters;
         float[] tmp = new float[m.size()];

         for (int i = 0; i < tmp.length; i++) {
            tmp[i] = this.baseDamage;
         }

         for (int i = 0; i < tmp.length; i++) {
            for (AbstractRelic rx : player.relics) {
               tmp[i] = rx.atDamageModify(tmp[i], this);
               if (this.baseDamage != (int)tmp[i]) {
                  this.isDamageModified = true;
               }
            }

            for (AbstractPower p : player.powers) {
               tmp[i] = p.atDamageGive(tmp[i], this.damageTypeForTurn, this);
            }

            tmp[i] = player.stance.atDamageGive(tmp[i], this.damageTypeForTurn, this);
            if (this.baseDamage != (int)tmp[i]) {
               this.isDamageModified = true;
            }
         }

         for (int i = 0; i < tmp.length; i++) {
            for (AbstractPower p : m.get(i).powers) {
               if (!m.get(i).isDying && !m.get(i).isEscaping) {
                  tmp[i] = p.atDamageReceive(tmp[i], this.damageTypeForTurn, this);
               }
            }
         }

         for (int i = 0; i < tmp.length; i++) {
            for (AbstractPower px : player.powers) {
               tmp[i] = px.atDamageFinalGive(tmp[i], this.damageTypeForTurn, this);
            }
         }

         for (int i = 0; i < tmp.length; i++) {
            for (AbstractPower px : m.get(i).powers) {
               if (!m.get(i).isDying && !m.get(i).isEscaping) {
                  tmp[i] = px.atDamageFinalReceive(tmp[i], this.damageTypeForTurn, this);
               }
            }
         }

         for (int i = 0; i < tmp.length; i++) {
            if (tmp[i] < 0.0F) {
               tmp[i] = 0.0F;
            }
         }

         this.multiDamage = new int[tmp.length];

         for (int ix = 0; ix < tmp.length; ix++) {
            if (this.baseDamage != MathUtils.floor(tmp[ix])) {
               this.isDamageModified = true;
            }

            this.multiDamage[ix] = MathUtils.floor(tmp[ix]);
         }

         this.damage = this.multiDamage[0];
      }
   }

   public void setAngle(float degrees, boolean immediate) {
      this.targetAngle = degrees;
      if (immediate) {
         this.angle = this.targetAngle;
      }
   }

   public void shrink() {
      this.targetDrawScale = 0.12F;
   }

   public void shrink(boolean immediate) {
      this.targetDrawScale = 0.12F;
      this.drawScale = 0.12F;
   }

   public void darken(boolean immediate) {
      this.darken = true;
      this.darkTimer = 0.3F;
      if (immediate) {
         this.tintColor.a = 1.0F;
         this.darkTimer = 0.0F;
      }
   }

   public void lighten(boolean immediate) {
      this.darken = false;
      this.darkTimer = 0.3F;
      if (immediate) {
         this.tintColor.a = 0.0F;
         this.darkTimer = 0.0F;
      }
   }

   private void updateColor() {
      if (this.darkTimer != 0.0F) {
         this.darkTimer = this.darkTimer - Gdx.graphics.getDeltaTime();
         if (this.darkTimer < 0.0F) {
            this.darkTimer = 0.0F;
         }

         if (this.darken) {
            this.tintColor.a = 1.0F - this.darkTimer * 1.0F / 0.3F;
         } else {
            this.tintColor.a = this.darkTimer * 1.0F / 0.3F;
         }
      }
   }

   public void superFlash(Color c) {
      this.flashVfx = new CardFlashVfx(this, c, true);
   }

   public void superFlash() {
      this.flashVfx = new CardFlashVfx(this, true);
   }

   public void flash() {
      this.flashVfx = new CardFlashVfx(this);
   }

   public void flash(Color c) {
      this.flashVfx = new CardFlashVfx(this, c);
   }

   public void unfadeOut() {
      this.fadingOut = false;
      this.transparency = 1.0F;
      this.targetTransparency = 1.0F;
      this.bannerColor.a = this.transparency;
      this.backColor.a = this.transparency;
      this.frameColor.a = this.transparency;
      this.bgColor.a = this.transparency;
      this.descBoxColor.a = this.transparency;
      this.imgFrameColor.a = this.transparency;
      this.frameShadowColor.a = this.transparency / 4.0F;
      this.renderColor.a = this.transparency;
      this.goldColor.a = this.transparency;
      if (this.frameOutlineColor != null) {
         this.frameOutlineColor.a = this.transparency;
      }
   }

   private void updateTransparency() {
      if (this.fadingOut && this.transparency != 0.0F) {
         this.transparency = this.transparency - Gdx.graphics.getDeltaTime() * 2.0F;
         if (this.transparency < 0.0F) {
            this.transparency = 0.0F;
         }
      } else if (this.transparency != this.targetTransparency) {
         this.transparency = this.transparency + Gdx.graphics.getDeltaTime() * 2.0F;
         if (this.transparency > this.targetTransparency) {
            this.transparency = this.targetTransparency;
         }
      }

      this.bannerColor.a = this.transparency;
      this.backColor.a = this.transparency;
      this.frameColor.a = this.transparency;
      this.bgColor.a = this.transparency;
      this.descBoxColor.a = this.transparency;
      this.imgFrameColor.a = this.transparency;
      this.frameShadowColor.a = this.transparency / 4.0F;
      this.renderColor.a = this.transparency;
      this.textColor.a = this.transparency;
      this.goldColor.a = this.transparency;
      if (this.frameOutlineColor != null) {
         this.frameOutlineColor.a = this.transparency;
      }
   }

   public void setAngle(float degrees) {
      this.setAngle(degrees, false);
   }

   protected String getCantPlayMessage() {
      return TEXT[13];
   }

   public void clearPowers() {
      this.resetAttributes();
      this.isDamageModified = false;
   }

   public static void debugPrintDetailedCardDataHeader() {
      logger.info(gameDataUploadHeader());
   }

   public static String gameDataUploadHeader() {
      GameDataStringBuilder builder = new GameDataStringBuilder();
      builder.addFieldData("name");
      builder.addFieldData("cardID");
      builder.addFieldData("rawDescription");
      builder.addFieldData("assetURL");
      builder.addFieldData("keywords");
      builder.addFieldData("color");
      builder.addFieldData("type");
      builder.addFieldData("rarity");
      builder.addFieldData("cost");
      builder.addFieldData("target");
      builder.addFieldData("damageType");
      builder.addFieldData("baseDamage");
      builder.addFieldData("baseBlock");
      builder.addFieldData("baseHeal");
      builder.addFieldData("baseDraw");
      builder.addFieldData("baseDiscard");
      builder.addFieldData("baseMagicNumber");
      builder.addFieldData("isMultiDamage");
      return builder.toString();
   }

   public void debugPrintDetailedCardData() {
      logger.info(this.gameDataUploadData());
   }

   protected void addToBot(AbstractGameAction action) {
      AbstractDungeon.actionManager.addToBottom(action);
   }

   protected void addToTop(AbstractGameAction action) {
      AbstractDungeon.actionManager.addToTop(action);
   }

   public String gameDataUploadData() {
      GameDataStringBuilder builder = new GameDataStringBuilder();
      builder.addFieldData(this.name);
      builder.addFieldData(this.cardID);
      builder.addFieldData(this.rawDescription);
      builder.addFieldData(this.assetUrl);
      builder.addFieldData(Arrays.toString(this.keywords.toArray()));
      builder.addFieldData(this.color.name());
      builder.addFieldData(this.type.name());
      builder.addFieldData(this.rarity.name());
      builder.addFieldData(this.cost);
      builder.addFieldData(this.target.name());
      builder.addFieldData(this.damageType.name());
      builder.addFieldData(this.baseDamage);
      builder.addFieldData(this.baseBlock);
      builder.addFieldData(this.baseHeal);
      builder.addFieldData(this.baseDraw);
      builder.addFieldData(this.baseDiscard);
      builder.addFieldData(this.baseMagicNumber);
      builder.addFieldData(this.isMultiDamage);
      return builder.toString();
   }

   @Override
   public String toString() {
      return this.name;
   }

   public int compareTo(AbstractCard other) {
      return this.cardID.compareTo(other.cardID);
   }

   public void setLocked() {
      this.isLocked = true;
      switch (this.type) {
         case ATTACK:
            this.portraitImg = ImageMaster.CARD_LOCKED_ATTACK;
            break;
         case POWER:
            this.portraitImg = ImageMaster.CARD_LOCKED_POWER;
            break;
         default:
            this.portraitImg = ImageMaster.CARD_LOCKED_SKILL;
      }

      this.initializeDescription();
   }

   public void unlock() {
      this.isLocked = false;
      this.portrait = cardAtlas.findRegion(this.assetUrl);
      if (this.portrait == null) {
         this.portrait = oldCardAtlas.findRegion(this.assetUrl);
      }
   }

   public HashMap<String, Serializable> getLocStrings() {
      HashMap<String, Serializable> cardData = new HashMap<>();
      this.initializeDescription();
      cardData.put("name", this.name);
      cardData.put("description", this.rawDescription);
      return cardData;
   }

   public String getMetricID() {
      String id = this.cardID;
      if (this.upgraded) {
         id = id + "+";
         if (this.timesUpgraded > 0) {
            id = id + this.timesUpgraded;
         }
      }

      return id;
   }

   public void triggerOnGlowCheck() {
   }

   public abstract AbstractCard makeCopy();

   static {
      TEXT = uiStrings.TEXT;
      LOCKED_STRING = cardRenderStrings.TEXT[0];
      UNKNOWN_STRING = cardRenderStrings.TEXT[1];
   }

   public static enum CardColor {
      RED,
      GREEN,
      BLUE,
      PURPLE,
      COLORLESS,
      CURSE;
   }

   public static enum CardRarity {
      BASIC,
      SPECIAL,
      COMMON,
      UNCOMMON,
      RARE,
      CURSE;
   }

   public static enum CardTags {
      HEALING,
      STRIKE,
      EMPTY,
      STARTER_DEFEND,
      STARTER_STRIKE;
   }

   public static enum CardTarget {
      ENEMY,
      ALL_ENEMY,
      SELF,
      NONE,
      SELF_AND_ENEMY,
      ALL;
   }

   public static enum CardType {
      ATTACK,
      SKILL,
      POWER,
      STATUS,
      CURSE;
   }
}
