package com.megacrit.cardcrawl.rooms;

public class EmptyRoom extends AbstractRoom {
   public EmptyRoom() {
      this.phase = AbstractRoom.RoomPhase.COMPLETE;
   }

   @Override
   public void onPlayerEntry() {
   }
}
