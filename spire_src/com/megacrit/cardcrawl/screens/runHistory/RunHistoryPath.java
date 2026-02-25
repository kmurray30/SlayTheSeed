package com.megacrit.cardcrawl.screens.runHistory;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.screens.stats.BattleStats;
import com.megacrit.cardcrawl.screens.stats.CampfireChoice;
import com.megacrit.cardcrawl.screens.stats.CardChoiceStats;
import com.megacrit.cardcrawl.screens.stats.EventStats;
import com.megacrit.cardcrawl.screens.stats.ObtainStats;
import com.megacrit.cardcrawl.screens.stats.RunData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class RunHistoryPath {
   public static final String BOSS_TREASURE_LABEL = "T!";
   public static final String HEART_LABEL = "<3";
   public static final int MAX_NODES_PER_ROW = 20;
   private RunData runData;
   public List<RunPathElement> pathElements;
   private int rows = 0;

   public RunHistoryPath() {
      this.pathElements = new ArrayList<>();
   }

   public void setRunData(RunData newData) {
      this.runData = newData;
      List<String> labels = new ArrayList<>();
      int choiceIndex = 0;
      int outcomeIndex = 0;
      List<String> choices = this.runData.path_taken;
      List<String> outcomes = this.runData.path_per_floor;
      if (choices == null) {
         choices = new LinkedList<>();
      }

      if (outcomes == null) {
         outcomes = new LinkedList<>();
      }

      int bossTreasureCount = 0;

      while (choiceIndex < choices.size() || outcomeIndex < outcomes.size()) {
         String choice = choiceIndex < choices.size() ? choices.get(choiceIndex) : "_";
         String outcome = outcomeIndex < outcomes.size() ? outcomes.get(outcomeIndex) : "_";
         if (outcome == null) {
            outcomeIndex++;
            if (bossTreasureCount < 2) {
               labels.add("T!");
               bossTreasureCount++;
            } else if (bossTreasureCount == 2) {
               labels.add("<3");
               bossTreasureCount++;
            } else {
               labels.add("null");
            }
         } else {
            if (choice.equals("?") && !outcome.equals("?")) {
               labels.add("?(" + outcome + ")");
            } else {
               labels.add(choice);
            }

            choiceIndex++;
            outcomeIndex++;
         }
      }

      HashMap<Integer, BattleStats> battlesByFloor = new HashMap<>();

      for (BattleStats battle : this.runData.damage_taken) {
         battlesByFloor.put(battle.floor, battle);
      }

      HashMap<Integer, EventStats> eventsByFloor = new HashMap<>();

      for (EventStats event : this.runData.event_choices) {
         eventsByFloor.put(event.floor, event);
      }

      HashMap<Integer, CardChoiceStats> cardsByFloor = new HashMap<>();

      for (CardChoiceStats choice : this.runData.card_choices) {
         cardsByFloor.put(choice.floor, choice);
      }

      HashMap<Integer, List<String>> relicsByFloor = new HashMap<>();
      if (this.runData.relics_obtained != null) {
         for (ObtainStats relicData : this.runData.relics_obtained) {
            if (!relicsByFloor.containsKey(relicData.floor)) {
               relicsByFloor.put(relicData.floor, new ArrayList<>());
            }

            relicsByFloor.get(relicData.floor).add(relicData.key);
         }
      }

      HashMap<Integer, List<String>> potionsByFloor = new HashMap<>();
      if (this.runData.potions_obtained != null) {
         for (ObtainStats potionData : this.runData.potions_obtained) {
            if (!potionsByFloor.containsKey(potionData.floor)) {
               potionsByFloor.put(potionData.floor, new ArrayList<>());
            }

            potionsByFloor.get(potionData.floor).add(potionData.key);
         }
      }

      HashMap<Integer, CampfireChoice> campfireChoicesByFloor = new HashMap<>();
      if (this.runData.campfire_choices != null) {
         for (CampfireChoice choice : this.runData.campfire_choices) {
            campfireChoicesByFloor.put(choice.floor, choice);
         }
      }

      HashMap<Integer, ArrayList<String>> purchasesByFloor = new HashMap<>();
      if (this.runData.items_purchased != null
         && this.runData.item_purchase_floors != null
         && this.runData.items_purchased.size() == this.runData.item_purchase_floors.size()) {
         for (int i = 0; i < this.runData.items_purchased.size(); i++) {
            int floor = this.runData.item_purchase_floors.get(i);
            String key = this.runData.items_purchased.get(i);
            if (!purchasesByFloor.containsKey(floor)) {
               purchasesByFloor.put(floor, new ArrayList<>());
            }

            purchasesByFloor.get(floor).add(key);
         }
      }

      HashMap<Integer, ArrayList<String>> purgesByFloor = new HashMap<>();
      if (this.runData.items_purged != null
         && this.runData.items_purged_floors != null
         && this.runData.items_purged.size() == this.runData.items_purged_floors.size()) {
         for (int i = 0; i < this.runData.items_purged.size(); i++) {
            int floor = this.runData.items_purged_floors.get(i);
            String key = this.runData.items_purged.get(i);
            if (!purgesByFloor.containsKey(floor)) {
               purgesByFloor.put(floor, new ArrayList<>());
            }

            purgesByFloor.get(floor).add(key);
         }
      }

      this.pathElements.clear();
      this.rows = 0;
      int bossRelicChoiceIndex = 0;
      int tmpColumn = 0;

      for (int i = 0; i < labels.size(); i++) {
         String label = labels.get(i);
         int floor = i + 1;
         RunPathElement element = new RunPathElement(label, floor);
         if (this.runData.current_hp_per_floor != null
            && this.runData.max_hp_per_floor != null
            && i < this.runData.current_hp_per_floor.size()
            && i < this.runData.max_hp_per_floor.size()) {
            element.addHpData(this.runData.current_hp_per_floor.get(i), this.runData.max_hp_per_floor.get(i));
         }

         if (this.runData.gold_per_floor != null && i < this.runData.gold_per_floor.size()) {
            element.addGoldData(this.runData.gold_per_floor.get(i));
         }

         if (battlesByFloor.containsKey(floor)) {
            element.addBattleData(battlesByFloor.get(floor));
         }

         if (eventsByFloor.containsKey(floor)) {
            element.addEventData(eventsByFloor.get(floor));
         }

         if (cardsByFloor.containsKey(floor)) {
            element.addCardChoiceData(cardsByFloor.get(floor));
         }

         if (relicsByFloor.containsKey(floor)) {
            element.addRelicObtainStats(relicsByFloor.get(floor));
         }

         if (potionsByFloor.containsKey(floor)) {
            element.addPotionObtainStats(potionsByFloor.get(floor));
         }

         if (campfireChoicesByFloor.containsKey(floor)) {
            element.addCampfireChoiceData(campfireChoicesByFloor.get(floor));
         }

         if (purchasesByFloor.containsKey(floor)) {
            element.addShopPurchaseData(purchasesByFloor.get(floor));
         }

         if (purgesByFloor.containsKey(floor)) {
            element.addPurgeData(purgesByFloor.get(floor));
         }

         if (label.equals("T!") && this.runData.boss_relics != null && bossRelicChoiceIndex < this.runData.boss_relics.size()) {
            element.addRelicObtainStats(this.runData.boss_relics.get(bossRelicChoiceIndex).picked);
            bossRelicChoiceIndex++;
         }

         this.pathElements.add(element);
         element.col = tmpColumn++;
         element.row = this.rows;
         if (this.isActEndNode(element) || i == labels.size() - 1) {
            tmpColumn = 0;
            this.rows++;
         }
      }

      this.rows = Math.max(this.rows, this.pathElements.size() / 20);
   }

   public void update() {
      boolean isHovered = false;

      for (RunPathElement element : this.pathElements) {
         element.update();
         if (element.isHovered()) {
            isHovered = true;
         }
      }

      if (isHovered) {
         CardCrawlGame.cursor.changeType(GameCursor.CursorType.INSPECT);
      }
   }

   private boolean isActEndNode(RunPathElement element) {
      return element.nodeType == RunPathElement.PathNodeType.BOSS_TREASURE || element.nodeType == RunPathElement.PathNodeType.HEART;
   }

   public void render(SpriteBatch sb, float x, float y) {
      float offsetX = x;
      float offsetY = y;
      int sinceOffset = 0;

      for (RunPathElement element : this.pathElements) {
         element.position(offsetX, offsetY);
         if (!this.isActEndNode(element) && sinceOffset <= 20) {
            offsetX += RunPathElement.getApproximateWidth();
            sinceOffset++;
         } else {
            offsetX = x;
            offsetY -= RunPathElement.getApproximateHeight();
            sinceOffset = 0;
         }
      }

      for (RunPathElement elementx : this.pathElements) {
         elementx.render(sb);
      }
   }

   public float approximateHeight() {
      return this.rows * RunPathElement.getApproximateHeight();
   }

   public float approximateMaxWidth() {
      return 16.5F * RunPathElement.getApproximateWidth();
   }
}
