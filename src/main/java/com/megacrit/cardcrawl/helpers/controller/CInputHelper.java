package com.megacrit.cardcrawl.helpers.controller;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;

import java.util.ArrayList;

/**
 * Headless override: skips LWJGL Display/controller init when running standalone seed search.
 * The original CInputHelper.initializeIfAble() loads org.lwjgl.opengl.Display which requires
 * LWJGL natives; we have no display in headless mode.
 */
public class CInputHelper {
    public static Array<Controller> controllers = null;
    public static Controller controller = null;
    public static ArrayList<CInputAction> actions = new ArrayList<>();
    public static CInputListener listener = null;
    private static boolean initializedController = false;
    public static ControllerModel model = null;

    public static void loadSettings() {
        CInputActionSet.load();
    }

    /**
     * In standalone/headless mode, skip controller init to avoid loading LWJGL Display.
     */
    public static void initializeIfAble() {
        if (Boolean.getBoolean("seedsearch.standalone")) {
            return;
        }
        if (!initializedController) {
            try {
                if (org.lwjgl.opengl.Display.isActive()) {
                    initializedController = true;
                    org.apache.logging.log4j.LogManager.getLogger(CInputHelper.class).info("[CONTROLLER] Utilizing DirectInput");
                    try {
                        controllers = Controllers.getControllers();
                    } catch (Exception ex) {
                        org.apache.logging.log4j.LogManager.getLogger(CInputHelper.class).info(ex.getMessage());
                    }
                    if (controllers == null) {
                        org.apache.logging.log4j.LogManager.getLogger(CInputHelper.class).info("[ERROR] getControllers() returned null");
                        return;
                    }
                    for (int i = 0; i < controllers.size; i++) {
                        org.apache.logging.log4j.LogManager.getLogger(CInputHelper.class).info("CONTROLLER[" + i + "] " + controllers.get(i).getName());
                    }
                    if (controllers.size != 0) {
                        Settings.isControllerMode = true;
                        Settings.isTouchScreen = false;
                        listener = new CInputListener();
                        controller = controllers.first();
                        controller.addListener(listener);
                        if (controller.getName().contains("360")) {
                            model = ControllerModel.XBOX_360;
                            ImageMaster.loadControllerImages(ControllerModel.XBOX_360);
                        } else if (controller.getName().contains("Xbox One")) {
                            model = ControllerModel.XBOX_ONE;
                            ImageMaster.loadControllerImages(ControllerModel.XBOX_ONE);
                        } else {
                            model = ControllerModel.XBOX_360;
                            ImageMaster.loadControllerImages(ControllerModel.XBOX_360);
                        }
                    } else {
                        org.apache.logging.log4j.LogManager.getLogger(CInputHelper.class).info("[CONTROLLER] No controllers detected");
                    }
                }
            } catch (UnsatisfiedLinkError | NoClassDefFoundError ignored) {
                // LWJGL not available (headless)
            }
        }
    }

    public static void setController(Controller controllerToUse) {
        Settings.isControllerMode = true;
        Settings.isTouchScreen = false;
        controller = controllerToUse;
        controller.addListener(listener);
        Controllers.removeListener(listener);
    }

    public static void setCursor(Hitbox hb) {
        if (hb != null) {
            int y = Settings.HEIGHT - (int) hb.cY;
            if (y < 0) {
                y = 0;
            } else if (y > Settings.HEIGHT) {
                y = Settings.HEIGHT;
            }
            Gdx.input.setCursorPosition((int) hb.cX, y);
        }
    }

    public static void updateLast() {
        for (CInputAction a : actions) {
            a.justPressed = false;
            a.justReleased = false;
        }
    }

    public static boolean listenerPress(int keycode) {
        for (CInputAction a : actions) {
            if (a.keycode == keycode) {
                a.justPressed = true;
                a.pressed = true;
                break;
            }
        }
        return false;
    }

    public static boolean listenerRelease(int keycode) {
        for (CInputAction a : actions) {
            if (a.keycode == keycode) {
                a.justReleased = true;
                a.pressed = false;
                break;
            }
        }
        return false;
    }

    public static boolean isJustPressed(int keycode) {
        for (CInputAction a : actions) {
            if (a.keycode == keycode) {
                return a.justPressed;
            }
        }
        return false;
    }

    public static boolean isJustReleased(int keycode) {
        for (CInputAction a : actions) {
            if (a.keycode == keycode) {
                return a.justReleased;
            }
        }
        return false;
    }

    public static void regainInputFocus() {
        CInputListener.remapping = false;
    }

    public static boolean isTopPanelActive() {
        return AbstractDungeon.topPanel != null
                && (AbstractDungeon.topPanel.selectPotionMode || AbstractDungeon.player.viewingRelics || !AbstractDungeon.topPanel.potionUi.isHidden);
    }

    public enum ControllerModel {
        XBOX_360,
        XBOX_ONE,
        PS3,
        PS4,
        STEAM,
        OTHER
    }
}
