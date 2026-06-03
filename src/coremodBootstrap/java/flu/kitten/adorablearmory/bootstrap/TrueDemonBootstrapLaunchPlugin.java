package flu.kitten.adorablearmory.bootstrap;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class TrueDemonBootstrapLaunchPlugin implements ILaunchPluginService {
    static final String PLUGIN_NAME = "adorablearmory_true_demon_transformer";
    private static final Logger LOGGER = LoggerFactory.getLogger(TrueDemonBootstrapLaunchPlugin.class);
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String LIVING_ENTITY = "net.minecraft.world.entity.LivingEntity";
    private static final String FORGE_HOOKS = "net.minecraftforge.common.ForgeHooks";
    private static final String SYNCHED_ENTITY_DATA = "net.minecraft.network.syncher.SynchedEntityData";
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String SERVER_PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String INVENTORY = "net.minecraft.world.entity.player.Inventory";
    private static final String ITEM_ENTITY = "net.minecraft.world.entity.item.ItemEntity";
    private static final String CORE = "flu/kitten/adorablearmory/entity/damagetype/TrueDemonCoreMod";
    private static final String DAMAGE_SOURCE_DESC = "Lnet/minecraft/world/damagesource/DamageSource;";
    private static final String ENTITY_DESC = "Lnet/minecraft/world/entity/Entity;";
    private static final String LIVING_ENTITY_DESC = "Lnet/minecraft/world/entity/LivingEntity;";
    private static final String PLAYER_DESC = "Lnet/minecraft/world/entity/player/Player;";
    private static final String SERVER_PLAYER_DESC = "Lnet/minecraft/server/level/ServerPlayer;";
    private static final String INVENTORY_DESC = "Lnet/minecraft/world/entity/player/Inventory;";
    private static final String ITEM_ENTITY_DESC = "Lnet/minecraft/world/entity/item/ItemEntity;";
    private static final String ITEM_STACK_DESC = "Lnet/minecraft/world/item/ItemStack;";
    private static final String EQUIPMENT_SLOT_DESC = "Lnet/minecraft/world/entity/EquipmentSlot;";
    private static final String INTERACTION_HAND_DESC = "Lnet/minecraft/world/InteractionHand;";
    private static final String ENTITY_DATA_ACCESSOR_DESC = "Lnet/minecraft/network/syncher/EntityDataAccessor;";

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        String className = classType.getClassName();
        return switch (className) {
            case ENTITY, LIVING_ENTITY, FORGE_HOOKS, SYNCHED_ENTITY_DATA, PLAYER, SERVER_PLAYER, INVENTORY, ITEM_ENTITY ->
                    EnumSet.of(Phase.BEFORE);
            default -> EnumSet.noneOf(Phase.class);
        };
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        if (phase != Phase.BEFORE) return false;

        String className = classType.getClassName();
        try {
            boolean changed = switch (className) {
                case ENTITY -> transformEntity(classNode);
                case LIVING_ENTITY -> transformLivingEntity(classNode);
                case FORGE_HOOKS -> transformForgeHooks(classNode);
                case SYNCHED_ENTITY_DATA -> transformSynchedEntityData(classNode);
                case PLAYER -> transformPlayer(classNode);
                case SERVER_PLAYER -> transformServerPlayer(classNode);
                case INVENTORY -> transformInventory(classNode);
                case ITEM_ENTITY -> transformItemEntity(classNode);
                default -> false;
            };
            if (changed) {
                LOGGER.info("[TrueDemon] patched {} bootstrap true demon hooks", className);
            }
            return changed;
        } catch (Throwable t) {
            LOGGER.error("[TrueDemon] failed to transform {}", className, t);
            return false;
        }
    }

    private static boolean transformLivingEntity(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isSetHealth(method)) {
                changed |= insertHeadOnce(method, "trueDemonSetHealth", patchFloatArg("trueDemonSetHealth"), 2);
            }
            if (isHeal(method)) {
                changed |= insertHeadOnce(method, "trueDemonHeal", patchFloatArg("trueDemonHeal"), 2);
            }
            if (isCheckTotemDeathProtection(method)) {
                changed |= insertHeadOnce(
                        method,
                        "shouldBlockTotem",
                        headBooleanCancelFalse(
                                "shouldBlockTotem",
                                "(" + LIVING_ENTITY_DESC + DAMAGE_SOURCE_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                }
                        ),
                        2
                );
            }
            if (isActuallyHurt(method)) {
                changed |= insertHeadOnce(method, "trueDemonActuallyHurtAmount", actualHurtAmountPatch(), 3);
            }
            if (isDamageSourceBlocked(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonBypassDamageBlock",
                        headBooleanCancelFalse(
                                "trueDemonBypassDamageBlock",
                                "(" + LIVING_ENTITY_DESC + DAMAGE_SOURCE_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                }
                        ),
                        2
                );
            }
            if (isSetItemInHand(method)) {
                changed |= insertHeadOnce(method, "trueDemonLivingSetStackInHand", livingSetStackInHandPatch(), 3);
            }
            if (isGetItemInHand(method)) {
                changed |= wrapReturnsOnce(
                        method,
                        Opcodes.ARETURN,
                        "trueDemonGetItemInHand",
                        "(" + ITEM_STACK_DESC + LIVING_ENTITY_DESC + ")" + ITEM_STACK_DESC,
                        insn -> insn.add(new VarInsnNode(Opcodes.ALOAD, 0)),
                        2
                );
            }
            if (isGetMainHandItem(method)) {
                changed |= wrapReturnsOnce(
                        method,
                        Opcodes.ARETURN,
                        "trueDemonGetMainHandItem",
                        "(" + ITEM_STACK_DESC + LIVING_ENTITY_DESC + ")" + ITEM_STACK_DESC,
                        insn -> insn.add(new VarInsnNode(Opcodes.ALOAD, 0)),
                        2
                );
            }
            if (isGetOffhandItem(method)) {
                changed |= wrapReturnsOnce(
                        method,
                        Opcodes.ARETURN,
                        "trueDemonGetOffhandItem",
                        "(" + ITEM_STACK_DESC + LIVING_ENTITY_DESC + ")" + ITEM_STACK_DESC,
                        insn -> insn.add(new VarInsnNode(Opcodes.ALOAD, 0)),
                        2
                );
            }
            if (isGetItemBySlot(method)) {
                changed |= wrapReturnsOnce(
                        method,
                        Opcodes.ARETURN,
                        "trueDemonGetItemBySlot",
                        "(" + ITEM_STACK_DESC + LIVING_ENTITY_DESC + EQUIPMENT_SLOT_DESC + ")" + ITEM_STACK_DESC,
                        insn -> {
                            insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        },
                        3
                );
            }
        }

        return changed;
    }

    private static boolean transformEntity(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isInvulnerableTo(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonBypassEntityInvulnerability",
                        headBooleanCancelFalse(
                                "trueDemonBypassEntityInvulnerability",
                                "(" + ENTITY_DESC + DAMAGE_SOURCE_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                }
                        ),
                        2
                );
            }
        }

        return changed;
    }

    private static boolean transformForgeHooks(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isForgeOnLivingDeath(method)) {
                changed |= wrapReturnsOnce(
                        method,
                        Opcodes.IRETURN,
                        "trueDemonForgeLivingDeath",
                        "(Z" + LIVING_ENTITY_DESC + DAMAGE_SOURCE_DESC + ")Z",
                        insn -> {
                            insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        },
                        3
                );
            }
            if (isForgeOnLivingAttack(method)) {
                changed |= wrapReturnsOnce(
                        method,
                        Opcodes.IRETURN,
                        "trueDemonForgeLivingAttack",
                        "(Z" + LIVING_ENTITY_DESC + DAMAGE_SOURCE_DESC + "F)Z",
                        insn -> {
                            insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            insn.add(new VarInsnNode(Opcodes.FLOAD, 2));
                        },
                        4
                );
            }
        }

        return changed;
    }

    private static boolean transformSynchedEntityData(ClassNode classNode) {
        boolean changed = false;
        String ownerField = findSynchedEntityOwnerField(classNode);

        for (MethodNode method : classNode.methods) {
            if (isSynchedEntityDataSet(method)) {
                changed |= insertHeadOnce(method, "trueDemonDataSet", synchedEntityDataSetPatch(classNode.name, ownerField), 3);
            }
        }

        return changed;
    }

    private static boolean transformPlayer(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isPlayerSetItemSlot(method)) {
                changed |= insertHeadOnce(method, "trueDemonPlayerSetItemSlot", playerSetItemSlotPatch(), 3);
            }
            if (isTick(method)) {
                changed |= insertVoidCallBeforeReturnsOnce(
                        method,
                        "trueDemonPlayerTick",
                        "(" + PLAYER_DESC + ")V",
                        insn -> insn.add(new VarInsnNode(Opcodes.ALOAD, 0)),
                        1
                );
            }
        }

        return changed;
    }

    private static boolean transformServerPlayer(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isServerPlayerDoTick(method)) {
                changed |= insertVoidCallBeforeReturnsOnce(
                        method,
                        "trueDemonServerPlayerPostTick",
                        "(" + SERVER_PLAYER_DESC + ")V",
                        insn -> insn.add(new VarInsnNode(Opcodes.ALOAD, 0)),
                        1
                );
            }
        }

        return changed;
    }

    private static boolean transformInventory(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isInventorySetItem(method)) {
                changed |= insertHeadOnce(method, "trueDemonInventorySetItem", inventorySetItemPatch(), 3);
            }
            if (isInventoryAdd(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonInventoryAdd",
                        headBooleanCancelFalse(
                                "trueDemonInventoryAdd",
                                "(" + INVENTORY_DESC + ITEM_STACK_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                }
                        ),
                        2
                );
            }
            if (isInventoryAddAt(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonInventoryAddAt",
                        headBooleanCancelFalse(
                                "trueDemonInventoryAddAt",
                                "(" + INVENTORY_DESC + "I" + ITEM_STACK_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 2));
                                }
                        ),
                        3
                );
            }
            if (isInventoryPlaceBack(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonPlaceBack",
                        headVoidCancel(
                                "trueDemonPlaceBack",
                                "(" + INVENTORY_DESC + ITEM_STACK_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                }
                        ),
                        2
                );
            }
            if (isInventoryPlaceBackNotify(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonPlaceBackNotify",
                        headVoidCancel(
                                "trueDemonPlaceBackNotify",
                                "(" + INVENTORY_DESC + ITEM_STACK_DESC + "Z)Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                    insn.add(new VarInsnNode(Opcodes.ILOAD, 2));
                                }
                        ),
                        3
                );
            }
            if (isInventorySetPickedItem(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonSetPickedItem",
                        headVoidCancel(
                                "trueDemonSetPickedItem",
                                "(" + INVENTORY_DESC + ITEM_STACK_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                }
                        ),
                        2
                );
            }
            if (isInventoryPickSlot(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonPickSlot",
                        headVoidCancel(
                                "trueDemonPickSlot",
                                "(" + INVENTORY_DESC + "I)Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
                                }
                        ),
                        2
                );
            }
            if (isInventoryGetArmor(method)) {
                changed |= wrapReturnsOnce(
                        method,
                        Opcodes.ARETURN,
                        "trueDemonInventoryGetArmor",
                        "(" + ITEM_STACK_DESC + INVENTORY_DESC + "I)" + ITEM_STACK_DESC,
                        insn -> {
                            insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
                        },
                        3
                );
            }
        }

        return changed;
    }

    private static boolean transformItemEntity(ClassNode classNode) {
        boolean changed = false;

        for (MethodNode method : classNode.methods) {
            if (isItemEntityPlayerTouch(method)) {
                changed |= insertHeadOnce(
                        method,
                        "trueDemonItemEntityTouch",
                        headVoidCancel(
                                "trueDemonItemEntityTouch",
                                "(" + ITEM_ENTITY_DESC + PLAYER_DESC + ")Z",
                                insn -> {
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                }
                        ),
                        2
                );
            }
        }

        return changed;
    }

    private static boolean isSetHealth(MethodNode method) {
        return hasName(method, "setHealth", "m_21153_", "t") && "(F)V".equals(method.desc);
    }

    private static boolean isHeal(MethodNode method) {
        return hasName(method, "heal", "m_5634_", "s") && "(F)V".equals(method.desc);
    }

    private static boolean isCheckTotemDeathProtection(MethodNode method) {
        return hasName(method, "checkTotemDeathProtection", "m_21262_", "h") && ("(" + DAMAGE_SOURCE_DESC + ")Z").equals(method.desc);
    }

    private static boolean isActuallyHurt(MethodNode method) {
        return hasName(method, "actuallyHurt", "m_6475_", "f") && ("(" + DAMAGE_SOURCE_DESC + "F)V").equals(method.desc);
    }

    private static boolean isDamageSourceBlocked(MethodNode method) {
        return hasName(method, "isDamageSourceBlocked", "m_21275_", "f") && ("(" + DAMAGE_SOURCE_DESC + ")Z").equals(method.desc);
    }

    private static boolean isSetItemInHand(MethodNode method) {
        return hasName(method, "setItemInHand", "m_21008_", "a") && ("(" + INTERACTION_HAND_DESC + ITEM_STACK_DESC + ")V").equals(method.desc);
    }

    private static boolean isGetItemInHand(MethodNode method) {
        return hasName(method, "getItemInHand", "m_21120_", "b") && ("(" + INTERACTION_HAND_DESC + ")" + ITEM_STACK_DESC).equals(method.desc);
    }

    private static boolean isGetMainHandItem(MethodNode method) {
        return hasName(method, "getMainHandItem", "m_21205_", "eO") && ("()" + ITEM_STACK_DESC).equals(method.desc);
    }

    private static boolean isGetOffhandItem(MethodNode method) {
        return hasName(method, "getOffhandItem", "m_21206_", "eP") && ("()" + ITEM_STACK_DESC).equals(method.desc);
    }

    private static boolean isGetItemBySlot(MethodNode method) {
        return hasName(method, "getItemBySlot", "m_6844_", "c") && ("(" + EQUIPMENT_SLOT_DESC + ")" + ITEM_STACK_DESC).equals(method.desc);
    }

    private static boolean isInvulnerableTo(MethodNode method) {
        return hasName(method, "isInvulnerableTo", "m_6673_", "b") && ("(" + DAMAGE_SOURCE_DESC + ")Z").equals(method.desc);
    }

    private static boolean isForgeOnLivingDeath(MethodNode method) {
        return "onLivingDeath".equals(method.name) && ("(" + LIVING_ENTITY_DESC + DAMAGE_SOURCE_DESC + ")Z").equals(method.desc);
    }

    private static boolean isForgeOnLivingAttack(MethodNode method) {
        return "onLivingAttack".equals(method.name) && ("(" + LIVING_ENTITY_DESC + DAMAGE_SOURCE_DESC + "F)Z").equals(method.desc);
    }

    private static boolean isSynchedEntityDataSet(MethodNode method) {
        return hasName(method, "set", "m_135381_", "b") && ("(" + ENTITY_DATA_ACCESSOR_DESC + "Ljava/lang/Object;)V").equals(method.desc);
    }

    private static boolean isPlayerSetItemSlot(MethodNode method) {
        return hasName(method, "setItemSlot", "m_8061_", "a") && ("(" + EQUIPMENT_SLOT_DESC + ITEM_STACK_DESC + ")V").equals(method.desc);
    }

    private static boolean isTick(MethodNode method) {
        return hasName(method, "tick", "m_8119_", "l") && "()V".equals(method.desc);
    }

    private static boolean isServerPlayerDoTick(MethodNode method) {
        return hasName(method, "doTick", "m_9240_", "m") && "()V".equals(method.desc);
    }

    private static boolean isInventorySetItem(MethodNode method) {
        return hasName(method, "setItem", "m_6836_", "a") && ("(I" + ITEM_STACK_DESC + ")V").equals(method.desc);
    }

    private static boolean isInventoryAdd(MethodNode method) {
        return hasName(method, "add", "m_36054_", "e") && ("(" + ITEM_STACK_DESC + ")Z").equals(method.desc);
    }

    private static boolean isInventoryAddAt(MethodNode method) {
        return hasName(method, "add", "m_36040_", "c") && ("(I" + ITEM_STACK_DESC + ")Z").equals(method.desc);
    }

    private static boolean isInventoryPlaceBack(MethodNode method) {
        return hasName(method, "placeItemBackInInventory", "m_150079_", "f") && ("(" + ITEM_STACK_DESC + ")V").equals(method.desc);
    }

    private static boolean isInventoryPlaceBackNotify(MethodNode method) {
        return hasName(method, "placeItemBackInInventory", "m_150076_", "a") && ("(" + ITEM_STACK_DESC + "Z)V").equals(method.desc);
    }

    private static boolean isInventorySetPickedItem(MethodNode method) {
        return hasName(method, "setPickedItem", "m_36012_", "a") && ("(" + ITEM_STACK_DESC + ")V").equals(method.desc);
    }

    private static boolean isInventoryPickSlot(MethodNode method) {
        return hasName(method, "pickSlot", "m_36038_", "c") && "(I)V".equals(method.desc);
    }

    private static boolean isInventoryGetArmor(MethodNode method) {
        return hasName(method, "getArmor", "m_36052_", "e") && ("(I)" + ITEM_STACK_DESC).equals(method.desc);
    }

    private static boolean isItemEntityPlayerTouch(MethodNode method) {
        return hasName(method, "playerTouch", "m_6123_", "b_") && ("(" + PLAYER_DESC + ")V").equals(method.desc);
    }

    private static boolean hasName(MethodNode method, String named, String srg, String obfuscated) {
        return named.equals(method.name) || srg.equals(method.name) || obfuscated.equals(method.name);
    }

    private static boolean insertHeadOnce(MethodNode method, String hookName, InsnList patch, int maxStack) {
        if (containsHook(method, hookName)) return false;
        method.instructions.insert(patch);
        method.maxStack = Math.max(method.maxStack, maxStack);
        return true;
    }

    private static boolean wrapReturnsOnce(MethodNode method, int returnOpcode, String hookName, String hookDesc, InsnBuilder loaders, int maxStack) {
        if (containsHook(method, hookName)) return false;

        List<AbstractInsnNode> returns = collectReturns(method, returnOpcode);
        if (returns.isEmpty()) return false;

        for (AbstractInsnNode node : returns) {
            InsnList insn = new InsnList();
            loaders.add(insn);
            insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, hookName, hookDesc, false));
            method.instructions.insertBefore(node, insn);
        }
        method.maxStack = Math.max(method.maxStack, maxStack);
        return true;
    }

    private static boolean insertVoidCallBeforeReturnsOnce(MethodNode method, String hookName, String hookDesc, InsnBuilder loaders, int maxStack) {
        if (containsHook(method, hookName)) return false;

        List<AbstractInsnNode> returns = collectReturns(method, Opcodes.RETURN);
        if (returns.isEmpty()) return false;

        for (AbstractInsnNode node : returns) {
            InsnList insn = new InsnList();
            loaders.add(insn);
            insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, hookName, hookDesc, false));
            method.instructions.insertBefore(node, insn);
        }
        method.maxStack = Math.max(method.maxStack, maxStack);
        return true;
    }

    private static List<AbstractInsnNode> collectReturns(MethodNode method, int returnOpcode) {
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node.getOpcode() == returnOpcode) {
                returns.add(node);
            }
        }
        return returns;
    }

    private static boolean containsHook(MethodNode method, String hookName) {
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode methodInsn && CORE.equals(methodInsn.owner) && hookName.equals(methodInsn.name)) {
                return true;
            }
        }
        return false;
    }

    private static InsnList patchFloatArg(String hookName) {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.FLOAD, 1));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, hookName, "(" + LIVING_ENTITY_DESC + "F)F", false));
        insn.add(new VarInsnNode(Opcodes.FSTORE, 1));
        return insn;
    }

    private static InsnList actualHurtAmountPatch() {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new VarInsnNode(Opcodes.FLOAD, 2));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, "trueDemonActuallyHurtAmount", "(" + LIVING_ENTITY_DESC + DAMAGE_SOURCE_DESC + "F)F", false));
        insn.add(new VarInsnNode(Opcodes.FSTORE, 2));
        return insn;
    }

    private static InsnList synchedEntityDataSetPatch(String classOwner, String ownerField) {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, classOwner, ownerField, ENTITY_DESC));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, "trueDemonDataSet", "(" + ENTITY_DESC + ENTITY_DATA_ACCESSOR_DESC + "Ljava/lang/Object;)Ljava/lang/Object;", false));
        insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
        return insn;
    }

    private static InsnList playerSetItemSlotPatch() {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, "trueDemonPlayerSetItemSlot", "(" + PLAYER_DESC + EQUIPMENT_SLOT_DESC + ITEM_STACK_DESC + ")" + ITEM_STACK_DESC, false));
        insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
        return insn;
    }

    private static InsnList livingSetStackInHandPatch() {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, "trueDemonLivingSetStackInHand", "(" + LIVING_ENTITY_DESC + INTERACTION_HAND_DESC + ITEM_STACK_DESC + ")" + ITEM_STACK_DESC, false));
        insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
        return insn;
    }

    private static InsnList inventorySetItemPatch() {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, "trueDemonInventorySetItem", "(" + INVENTORY_DESC + "I" + ITEM_STACK_DESC + ")" + ITEM_STACK_DESC, false));
        insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
        return insn;
    }

    private static InsnList headBooleanCancelFalse(String hookName, String hookDesc, InsnBuilder loaders) {
        InsnList insn = new InsnList();
        LabelNode continueLabel = new LabelNode();
        loaders.add(insn);
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, hookName, hookDesc, false));
        insn.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        insn.add(new InsnNode(Opcodes.ICONST_0));
        insn.add(new InsnNode(Opcodes.IRETURN));
        insn.add(continueLabel);
        return insn;
    }

    private static InsnList headVoidCancel(String hookName, String hookDesc, InsnBuilder loaders) {
        InsnList insn = new InsnList();
        LabelNode continueLabel = new LabelNode();
        loaders.add(insn);
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE, hookName, hookDesc, false));
        insn.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        insn.add(new InsnNode(Opcodes.RETURN));
        insn.add(continueLabel);
        return insn;
    }

    private static String findSynchedEntityOwnerField(ClassNode classNode) {
        for (FieldNode field : classNode.fields) {
            if ((field.access & Opcodes.ACC_STATIC) == 0 && ENTITY_DESC.equals(field.desc)) {
                return field.name;
            }
        }
        return "f_135344_";
    }

    private interface InsnBuilder {
        void add(InsnList insn);
    }
}
