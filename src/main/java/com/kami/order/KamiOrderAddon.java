package com.kami.order;

import com.kami.order.modules.KamiOrderBot;
import com.kami.order.modules.KamiSpawnerProtect;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/**
 * Entrypoint — KamiOrderBot (Auto Order).
 * Author: kami · MC 1.21.11 / Java 21 / Meteor 1.21.11
 */
public class KamiOrderAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("KamiOrderBot Addon initialized by kami");
        Modules.get().add(new KamiOrderBot());
        Modules.get().add(new KamiSpawnerProtect());
    }

    @Override
    public void onRegisterCategories() {
        // Categories.Misc
    }

    @Override
    public String getPackage() {
        return "com.kami.order";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("kami", "kami-order-bot");
    }
}
