package com.megacrit.cardcrawl.rooms;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.beyond.SpireHeart;

public class VictoryRoom extends AbstractRoom {
   public VictoryRoom.EventType eType;

   public VictoryRoom(VictoryRoom.EventType type) {
      this.phase = AbstractRoom.RoomPhase.EVENT;
      this.eType = type;
   }

   @Override
   public void onPlayerEntry() {
      AbstractDungeon.overlayMenu.proceedButton.hide();
      switch (this.eType) {
         case HEART:
            this.event = new SpireHeart();
            this.event.onEnterRoom();
         case NONE:
      }
   }

   @Override
   public void update() {
      super.update();
      if (!AbstractDungeon.isScreenUp) {
         this.event.update();
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      if (this.event != null) {
         this.event.renderRoomEventPanel(sb);
         this.event.render(sb);
      }

      super.render(sb);
   }

   @Override
   public void renderAboveTopPanel(SpriteBatch sb) {
      super.renderAboveTopPanel(sb);
      if (this.event != null) {
         this.event.renderAboveTopPanel(sb);
      }
   }

   public static enum EventType {
      HEART,
      NONE;
   }
}
