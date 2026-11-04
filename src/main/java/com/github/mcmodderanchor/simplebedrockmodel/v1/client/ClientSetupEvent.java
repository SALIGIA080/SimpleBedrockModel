package com.github.mcmodderanchor.simplebedrockmodel.v1.client;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat;

public class ClientSetupEvent {
    public static void onClientSetup() {
        SodiumCompat.init();
    }
}
