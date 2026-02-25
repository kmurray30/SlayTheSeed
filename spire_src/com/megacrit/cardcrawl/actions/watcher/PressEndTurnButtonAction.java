package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PressEndTurnButtonAction extends AbstractGameAction {
   @Override
   public void update() {
      AbstractDungeon.actionManager.callEndTurnEarlySequence();
      this.isDone = true;
   }
}
