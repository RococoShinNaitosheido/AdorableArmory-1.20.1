package flu.kitten.adorablearmory.entity.damagetype;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public class Capabilities {
    public static final Capability<ITrueDemonEffect> TRUE_DEMON_EFFECT = CapabilityManager.get(new CapabilityToken<>(){});
}
