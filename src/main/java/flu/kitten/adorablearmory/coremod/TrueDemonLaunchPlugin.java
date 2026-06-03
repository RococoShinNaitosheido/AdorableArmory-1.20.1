package flu.kitten.adorablearmory.coremod;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public final class TrueDemonLaunchPlugin implements ILaunchPluginService {
    static final String PLUGIN_NAME = "adorablearmory_true_demon_transformer";
    private static final Logger LOGGER = LoggerFactory.getLogger(TrueDemonLaunchPlugin.class);
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String LIVING_ENTITY = "net.minecraft.world.entity.LivingEntity";
    private static final String DAMAGE_SOURCE_DESC = "Lnet/minecraft/world/damagesource/DamageSource;";
    private static final String CORE = "flu/kitten/adorablearmory/entity/damagetype/TrueDemonCoreMod";

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        String className = classType.getClassName();
        if (ENTITY.equals(className) || LIVING_ENTITY.equals(className)) {
            return EnumSet.of(Phase.BEFORE);
        }
        return EnumSet.noneOf(Phase.class);
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        if (phase != Phase.BEFORE) return false;

        String className = classType.getClassName();
        try {
            if (LIVING_ENTITY.equals(className)) {
                boolean changed = transformLivingEntity(classNode);
                if (changed) {
                    LOGGER.info("[TrueDemon] patched LivingEntity true demon damage internals");
                }
                return changed;
            }
            if (ENTITY.equals(className)) {
                boolean changed = transformEntity(classNode);
                if (changed) {
                    LOGGER.info("[TrueDemon] patched Entity invulnerability hook");
                }
                return changed;
            }
            return false;
        } catch (Throwable t) {
            LOGGER.error("[TrueDemon] failed to transform {}", className, t);
            return false;
        }
    }

    private static boolean transformLivingEntity(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isActuallyHurt(method)) {
                method.instructions.insert(actualHurtAmountPatch());
                method.maxStack = Math.max(method.maxStack, 3);
                changed = true;
            } else if (isDamageSourceBlocked(method)) {
                method.instructions.insert(bypassLivingBooleanPatch("trueDemonBypassDamageBlock"));
                method.maxStack = Math.max(method.maxStack, 2);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean transformEntity(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isInvulnerableTo(method)) {
                method.instructions.insert(bypassEntityBooleanPatch());
                method.maxStack = Math.max(method.maxStack, 2);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isActuallyHurt(MethodNode method) {
        return hasName(method, "actuallyHurt", "m_6475_", "f")
                && ("(" + DAMAGE_SOURCE_DESC + "F)V").equals(method.desc);
    }

    private static boolean isDamageSourceBlocked(MethodNode method) {
        return hasName(method, "isDamageSourceBlocked", "m_21275_", "f")
                && ("(" + DAMAGE_SOURCE_DESC + ")Z").equals(method.desc);
    }

    private static boolean isInvulnerableTo(MethodNode method) {
        return hasName(method, "isInvulnerableTo", "m_6673_", "b")
                && ("(" + DAMAGE_SOURCE_DESC + ")Z").equals(method.desc);
    }

    private static boolean hasName(MethodNode method, String named, String srg, String obfuscated) {
        return named.equals(method.name) || srg.equals(method.name) || obfuscated.equals(method.name);
    }

    private static InsnList actualHurtAmountPatch() {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new VarInsnNode(Opcodes.FLOAD, 2));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                CORE,
                "trueDemonActuallyHurtAmount",
                "(Lnet/minecraft/world/entity/LivingEntity;" + DAMAGE_SOURCE_DESC + "F)F",
                false
        ));
        insn.add(new VarInsnNode(Opcodes.FSTORE, 2));
        return insn;
    }

    private static InsnList bypassLivingBooleanPatch(String hookName) {
        InsnList insn = new InsnList();
        LabelNode continueLabel = new LabelNode();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                CORE,
                hookName,
                "(Lnet/minecraft/world/entity/LivingEntity;" + DAMAGE_SOURCE_DESC + ")Z",
                false
        ));
        insn.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        insn.add(new InsnNode(Opcodes.ICONST_0));
        insn.add(new InsnNode(Opcodes.IRETURN));
        insn.add(continueLabel);
        return insn;
    }

    private static InsnList bypassEntityBooleanPatch() {
        InsnList insn = new InsnList();
        LabelNode continueLabel = new LabelNode();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                CORE,
                "trueDemonBypassEntityInvulnerability",
                "(Lnet/minecraft/world/entity/Entity;" + DAMAGE_SOURCE_DESC + ")Z",
                false
        ));
        insn.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        insn.add(new InsnNode(Opcodes.ICONST_0));
        insn.add(new InsnNode(Opcodes.IRETURN));
        insn.add(continueLabel);
        return insn;
    }
}
