package seedsearch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.backends.headless.HeadlessNativesLoader;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import java.nio.IntBuffer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Entry point for regression tests. Sets seedsearch.regression.test=true so StandaloneLauncher
 * dispatches to RegressionTest.run() instead of SeedSearch.search().
 */
public class RegressionTestMain {

    private static final int GL_COMPILE_STATUS = 35713;
    private static final int GL_LINK_STATUS = 35714;

    private static GL20 createHeadlessGL20() {
        GL20 gl = mock(GL20.class);
        when(gl.glCreateShader(anyInt())).thenReturn(1);
        when(gl.glCreateProgram()).thenReturn(1);
        doAnswer(invocation -> {
            IntBuffer params = invocation.getArgument(2);
            int pname = invocation.getArgument(1);
            if (pname == GL_COMPILE_STATUS) {
                params.put(0, 1);
            }
            return null;
        }).when(gl).glGetShaderiv(anyInt(), anyInt(), any(IntBuffer.class));
        doAnswer(invocation -> {
            IntBuffer params = invocation.getArgument(2);
            int pname = invocation.getArgument(1);
            if (pname == GL_LINK_STATUS) {
                params.put(0, 1);
            }
            return null;
        }).when(gl).glGetProgramiv(anyInt(), anyInt(), any(IntBuffer.class));
        return gl;
    }

    public static void main(String[] args) {
        System.setProperty("seedsearch.standalone", "true");
        System.setProperty("seedsearch.regression.test", "true");

        try {
            Configurator.setRootLevel(Level.OFF);
            HeadlessNativesLoader.load();
            Gdx.graphics = new MockGraphics();
            Gdx.gl = Gdx.gl20 = createHeadlessGL20();
            Gdx.audio = null;

            HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
            new HeadlessApplication(new CardCrawlGame(config.preferencesDirectory), config);
        } catch (Exception e) {
            e.printStackTrace();
            if (Gdx.app != null) {
                Gdx.app.exit();
            }
        }
    }
}
