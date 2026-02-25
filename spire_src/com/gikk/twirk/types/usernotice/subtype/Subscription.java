package com.gikk.twirk.types.usernotice.subtype;

import java.util.Optional;

public interface Subscription {
   SubscriptionPlan getSubscriptionPlan();

   int getMonths();

   int getStreak();

   boolean isSharedStreak();

   String getSubscriptionPlanName();

   boolean isResub();

   boolean isGift();

   Optional<SubscriptionGift> getSubscriptionGift();
}
