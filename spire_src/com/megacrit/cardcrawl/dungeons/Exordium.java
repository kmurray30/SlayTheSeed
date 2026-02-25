package com.megacrit.cardcrawl.dungeons;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.SaveHelper;
import com.megacrit.cardcrawl.helpers.TipTracker;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.MonsterInfo;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.rooms.EmptyRoom;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.scenes.TheBottomScene;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import java.util.ArrayList;
import java.util.Collections;

public class Exordium extends AbstractDungeon {
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("Exordium");
   public static final String[] TEXT;
   public static final String NAME;
   public static final String ID = "Exordium";

   public Exordium(AbstractPlayer p, ArrayList<String> emptyList) {
      super(NAME, "Exordium", p, emptyList);
      this.initializeRelicList();
      if (Settings.isEndless) {
         if (floorNum <= 1) {
            blightPool.clear();
            blightPool = new ArrayList<>();
         }
      } else {
         blightPool.clear();
      }

      if (scene != null) {
         scene.dispose();
      }

      scene = new TheBottomScene();
      scene.randomizeScene();
      fadeColor = Color.valueOf("1e0f0aff");
      sourceFadeColor = Color.valueOf("1e0f0aff");
      this.initializeSpecialOneTimeEventList();
      this.initializeLevelSpecificChances();
      mapRng = new Random(Settings.seed + AbstractDungeon.actNum);
      generateMap();
      CardCrawlGame.music.changeBGM(id);
      AbstractDungeon.currMapNode = new MapRoomNode(0, -1);
      if (!Settings.isShowBuild && TipTracker.tips.get("NEOW_SKIP")) {
         AbstractDungeon.currMapNode.room = new NeowRoom(false);
         if (AbstractDungeon.floorNum > 1) {
            SaveHelper.saveIfAppropriate(SaveFile.SaveType.ENDLESS_NEOW);
         } else {
            SaveHelper.saveIfAppropriate(SaveFile.SaveType.ENTER_ROOM);
         }
      } else {
         AbstractDungeon.currMapNode.room = new EmptyRoom();
      }
   }

   public Exordium(AbstractPlayer p, SaveFile saveFile) {
      super(NAME, p, saveFile);
      CardCrawlGame.dungeon = this;
      if (scene != null) {
         scene.dispose();
      }

      scene = new TheBottomScene();
      fadeColor = Color.valueOf("1e0f0aff");
      sourceFadeColor = Color.valueOf("1e0f0aff");
      this.initializeLevelSpecificChances();
      miscRng = new Random(Settings.seed + saveFile.floor_num);
      CardCrawlGame.music.changeBGM(id);
      mapRng = new Random(Settings.seed + saveFile.act_num);
      generateMap();
      firstRoomChosen = true;
      this.populatePathTaken(saveFile);
      if (this.isLoadingIntoNeow(saveFile)) {
         AbstractDungeon.firstRoomChosen = false;
      }
   }

   @Override
   protected void initializeLevelSpecificChances() {
      shopRoomChance = 0.05F;
      restRoomChance = 0.12F;
      treasureRoomChance = 0.0F;
      eventRoomChance = 0.22F;
      eliteRoomChance = 0.08F;
      smallChestChance = 50;
      mediumChestChance = 33;
      largeChestChance = 17;
      commonRelicChance = 50;
      uncommonRelicChance = 33;
      rareRelicChance = 17;
      colorlessRareChance = 0.3F;
      cardUpgradedChance = 0.0F;
   }

   @Override
   protected void generateMonsters() {
      this.generateWeakEnemies(3);
      this.generateStrongEnemies(12);
      this.generateElites(10);
   }

   @Override
   protected void generateWeakEnemies(int count) {
      ArrayList<MonsterInfo> monsters = new ArrayList<>();
      monsters.add(new MonsterInfo("Cultist", 2.0F));
      monsters.add(new MonsterInfo("Jaw Worm", 2.0F));
      monsters.add(new MonsterInfo("2 Louse", 2.0F));
      monsters.add(new MonsterInfo("Small Slimes", 2.0F));
      MonsterInfo.normalizeWeights(monsters);
      this.populateMonsterList(monsters, count, false);
   }

   @Override
   protected void generateStrongEnemies(int count) {
      ArrayList<MonsterInfo> monsters = new ArrayList<>();
      monsters.add(new MonsterInfo("Blue Slaver", 2.0F));
      monsters.add(new MonsterInfo("Gremlin Gang", 1.0F));
      monsters.add(new MonsterInfo("Looter", 2.0F));
      monsters.add(new MonsterInfo("Large Slime", 2.0F));
      monsters.add(new MonsterInfo("Lots of Slimes", 1.0F));
      monsters.add(new MonsterInfo("Exordium Thugs", 1.5F));
      monsters.add(new MonsterInfo("Exordium Wildlife", 1.5F));
      monsters.add(new MonsterInfo("Red Slaver", 1.0F));
      monsters.add(new MonsterInfo("3 Louse", 2.0F));
      monsters.add(new MonsterInfo("2 Fungi Beasts", 2.0F));
      MonsterInfo.normalizeWeights(monsters);
      this.populateFirstStrongEnemy(monsters, this.generateExclusions());
      this.populateMonsterList(monsters, count, false);
   }

   @Override
   protected void generateElites(int count) {
      ArrayList<MonsterInfo> monsters = new ArrayList<>();
      monsters.add(new MonsterInfo("Gremlin Nob", 1.0F));
      monsters.add(new MonsterInfo("Lagavulin", 1.0F));
      monsters.add(new MonsterInfo("3 Sentries", 1.0F));
      MonsterInfo.normalizeWeights(monsters);
      this.populateMonsterList(monsters, count, true);
   }

   @Override
   protected ArrayList<String> generateExclusions() {
      ArrayList<String> retVal = new ArrayList<>();
      String var2 = monsterList.get(monsterList.size() - 1);
      switch (var2) {
         case "Looter":
            retVal.add("Exordium Thugs");
         case "Jaw Worm":
         case "Cultist":
         default:
            break;
         case "Blue Slaver":
            retVal.add("Red Slaver");
            retVal.add("Exordium Thugs");
            break;
         case "2 Louse":
            retVal.add("3 Louse");
            break;
         case "Small Slimes":
            retVal.add("Large Slime");
            retVal.add("Lots of Slimes");
      }

      return retVal;
   }

   @Override
   protected void initializeBoss() {
      bossList.clear();
      if (Settings.isDailyRun) {
         bossList.add("The Guardian");
         bossList.add("Hexaghost");
         bossList.add("Slime Boss");
         Collections.shuffle(bossList, new java.util.Random(monsterRng.randomLong()));
      } else if (!UnlockTracker.isBossSeen("GUARDIAN")) {
         bossList.add("The Guardian");
      } else if (!UnlockTracker.isBossSeen("GHOST")) {
         bossList.add("Hexaghost");
      } else if (!UnlockTracker.isBossSeen("SLIME")) {
         bossList.add("Slime Boss");
      } else {
         bossList.add("The Guardian");
         bossList.add("Hexaghost");
         bossList.add("Slime Boss");
         Collections.shuffle(bossList, new java.util.Random(monsterRng.randomLong()));
      }

      if (bossList.size() == 1) {
         bossList.add(bossList.get(0));
      } else if (bossList.isEmpty()) {
         logger.warn("Boss list was empty. How?");
         bossList.add("The Guardian");
         bossList.add("Hexaghost");
         bossList.add("Slime Boss");
         Collections.shuffle(bossList, new java.util.Random(monsterRng.randomLong()));
      }

      if (Settings.isDemo) {
         bossList.clear();
         bossList.add("Hexaghost");
      }
   }

   @Override
   protected void initializeEventList() {
      eventList.add("Big Fish");
      eventList.add("The Cleric");
      eventList.add("Dead Adventurer");
      eventList.add("Golden Idol");
      eventList.add("Golden Wing");
      eventList.add("World of Goop");
      eventList.add("Liars Game");
      eventList.add("Living Wall");
      eventList.add("Mushrooms");
      eventList.add("Scrap Ooze");
      eventList.add("Shining Light");
   }

   @Override
   protected void initializeShrineList() {
      shrineList.add("Match and Keep!");
      shrineList.add("Golden Shrine");
      shrineList.add("Transmorgrifier");
      shrineList.add("Purifier");
      shrineList.add("Upgrade Shrine");
      shrineList.add("Wheel of Change");
   }

   @Override
   protected void initializeEventImg() {
      if (eventBackgroundImg != null) {
         eventBackgroundImg.dispose();
         eventBackgroundImg = null;
      }

      eventBackgroundImg = ImageMaster.loadImage("images/ui/event/panel.png");
   }

   static {
      TEXT = uiStrings.TEXT;
      NAME = TEXT[0];
   }
}
