package com.megacrit.cardcrawl.actions;

public class ActionLogEntry {
   public AbstractGameAction.ActionType type;

   public ActionLogEntry(AbstractGameAction.ActionType type) {
      this.type = type;
   }

   @Override
   public String toString() {
      return this.type.toString();
   }
}
