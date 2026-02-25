package de.robojumper.ststwitch;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import java.io.File;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TwitchPanel {
   private static final Logger logger = LogManager.getLogger(TwitchPanel.class.getName());
   private static Texture TWITCH_RED;
   private static Texture TWITCH_YELLOW;
   private static Texture TWITCH_GREEN;
   private static final float SIZE = 64.0F;
   public TwitchConnection connection;
   private TwitchVoter voter;
   private float iconScale = 1.0F;
   private float x;
   private float y;
   private Hitbox hb;

   public TwitchPanel(TwitchConnection twitchConnection) {
      this.connection = twitchConnection;
      logger.info("Instantiated twitch panel");
      if (TWITCH_RED == null) {
         TWITCH_RED = getTexture("images" + File.separator + "twitch_assets" + File.separator + "twitch_red.png");
         TWITCH_YELLOW = getTexture("images" + File.separator + "twitch_assets" + File.separator + "twitch_orange.png");
         TWITCH_GREEN = getTexture("images" + File.separator + "twitch_assets" + File.separator + "twitch_green.png");
      }

      logger.info("Starting Twitch connection");
      this.connection.setStatus(settingsGetTwitchEnabled());
      this.connection.start();
      this.hb = new Hitbox(0.0F, 0.0F, 64.0F * Settings.scale, 64.0F * Settings.scale);
   }

   static TwitchConnection getTwitch() {
      return AbstractDungeon.topPanel.twitch.<TwitchConnection>map(twitchPanel -> twitchPanel.connection).orElse(null);
   }

   public static Optional<TwitchVoter> getDefaultVoter() {
      if (AbstractDungeon.topPanel.twitch.isPresent()) {
         TwitchPanel twitchPanel = AbstractDungeon.topPanel.twitch.get();
         if (twitchPanel.voter == null) {
            twitchPanel.voter = new TwitchVoter(twitchPanel.connection.getTwitchConfig());
         }

         return Optional.of(twitchPanel.voter);
      } else {
         return Optional.empty();
      }
   }

   public void setPosition(float x, float y) {
      this.x = x;
      this.y = y;
   }

   private static Texture getTexture(String path) {
      logger.info("Loading: " + path);
      Texture tex = new Texture(path);
      tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      return tex;
   }

   private static boolean settingsGetTwitchEnabled() {
      return true;
   }

   public void unhover() {
   }

   public void update() {
      if (settingsGetTwitchEnabled()) {
         this.updateInstructions();
         this.updateInput();
         this.updateTwitch();
      } else {
         this.connection.setStatus(false);
      }
   }

   private void updateTwitch() {
      this.connection.update();
      if (this.voter != null) {
         this.voter.update();
      }
   }

   private void updateInstructions() {
      if (this.connection.popPulse()) {
         this.iconScale = 1.5F;
      }

      this.iconScale = MathHelper.popLerpSnap(this.iconScale, 1.0F);
   }

   private void updateInput() {
      this.hb.update(this.x, this.y - 64.0F * Settings.scale * this.iconScale);
      if (this.hb.hovered && InputHelper.justClickedLeft) {
         InputHelper.justClickedLeft = false;
         this.connection.toggleStatus();
      }
   }

   public void render(SpriteBatch sb) {
      if (settingsGetTwitchEnabled()) {
         Texture useTex = null;
         switch (this.connection.getConnectionStatus()) {
            case CONNECTED:
               useTex = TWITCH_GREEN;
               break;
            case CONNECTING:
            case DISCONNECTING:
               useTex = TWITCH_YELLOW;
               break;
            case DISCONNECTED:
               useTex = TWITCH_RED;
         }

         sb.setColor(Color.WHITE);
         sb.draw(
            useTex,
            this.x,
            this.y - 64.0F * Settings.scale,
            64.0F * Settings.scale * 0.5F,
            64.0F * Settings.scale * 0.5F,
            64.0F * Settings.scale,
            64.0F * Settings.scale,
            this.iconScale,
            this.iconScale,
            0.0F,
            0,
            0,
            128,
            128,
            false,
            false
         );
      }
   }
}
