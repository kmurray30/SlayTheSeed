package seedsearch;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.util.ArrayList;

/**
 * Static hooks for standalone mode. spire_src sets these when running without the game GUI.
 */
public class StandaloneHooks {

    public static AbstractRelic obtainedRelic;
    public static ArrayList<AbstractCard> rewardCards;
    public static ArrayList<AbstractCard> obtainedCards = new ArrayList<>();
    /** Encounter IDs populated by MonsterHelper.uploadEnemyData for IdChecker. */
    public static ArrayList<String> encounterIds = new ArrayList<>();

    private static Texture placeholderTexture;

    public static void resetObtainedCards() {
        obtainedCards = new ArrayList<>();
    }

    /**
     * Returns a 1x1 placeholder texture for standalone mode (no assets).
     */
    public static Texture getPlaceholderTexture() {
        if (placeholderTexture == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(0x00000000);
            pixmap.fill();
            placeholderTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        return placeholderTexture;
    }
}
