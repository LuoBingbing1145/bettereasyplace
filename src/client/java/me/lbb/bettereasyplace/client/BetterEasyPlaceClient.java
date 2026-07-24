package me.lbb.bettereasyplace.client;

import fi.dy.masa.malilib.event.InputEventHandler;
import me.lbb.bettereasyplace.event.InputHandler;
import net.fabricmc.api.ClientModInitializer;

public class BetterEasyPlaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
        InputEventHandler.getInputManager().registerKeyboardInputHandler(InputHandler.getInstance());
        InputEventHandler.getInputManager().registerMouseInputHandler(InputHandler.getInstance());
    }
}
