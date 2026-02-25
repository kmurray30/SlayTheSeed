package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PerfectedFormAction extends AbstractGameAction {
   @Override
   public void update() {
      this.isDone = true;
      boolean hadCalm = false;
      boolean hadCourage = false;
      boolean hadWrath = false;
      if (!AbstractDungeon.player.stance.ID.equals("Divinity")) {
      }
   }
}
