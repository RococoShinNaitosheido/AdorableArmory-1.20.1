function initializeCoreMod() {
    var Opcodes = Java.type("org.objectweb.asm.Opcodes");
    var InsnList = Java.type("org.objectweb.asm.tree.InsnList");
    var ASMAPI = Java.type("net.minecraftforge.coremod.api.ASMAPI");
    var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
    var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
    var FieldInsnNode = Java.type("org.objectweb.asm.tree.FieldInsnNode");
    var JumpInsnNode = Java.type("org.objectweb.asm.tree.JumpInsnNode");
    var LabelNode = Java.type("org.objectweb.asm.tree.LabelNode");
    var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");

    var core = "flu/kitten/adorablearmory/entity/damagetype/TrueDemonCoreMod";

    function insertHead(methodNode, list) {
        methodNode.instructions.insert(list);
        return methodNode;
    }

    function patchFloatArg(methodNode, hookName) {
        var insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new VarInsnNode(Opcodes.FLOAD, 1));
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, "(Lnet/minecraft/world/entity/LivingEntity;F)F", false));
        insn.add(new VarInsnNode(Opcodes.FSTORE, 1));
        return insertHead(methodNode, insn);
    }

    function patchHeadBoolShortCircuit(methodNode, hookName, hookDesc, returnConstOpcode, loaders) {
        var insn = new InsnList();
        var cont = new LabelNode();

        loaders(insn);
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, hookDesc, false));
        insn.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        insn.add(new InsnNode(returnConstOpcode));
        insn.add(new InsnNode(Opcodes.IRETURN));
        insn.add(cont);

        return insertHead(methodNode, insn);
    }

    function wrapReturns(methodNode, returnOpcode, hookName, hookDesc, loaders) {
        var it = methodNode.instructions.iterator();
        while (it.hasNext()) {
            var node = it.next();
            if (node.getOpcode() === returnOpcode) {
                var insn = new InsnList();
                loaders(insn);
                insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, hookDesc, false));
                methodNode.instructions.insertBefore(node, insn);
            }
        }
        return methodNode;
    }

    function patchPlayerSetItemSlot(methodNode, hookName) {
        var insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0)); // Player self
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1)); // EquipmentSlot
        insn.add(new VarInsnNode(Opcodes.ALOAD, 2)); // ItemStack
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", false));
        insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
        return insertHead(methodNode, insn);
    }

    function patchLivingSetStackInHand(methodNode, hookName) {
        var insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0)); // LivingEntity self
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1)); // InteractionHand
        insn.add(new VarInsnNode(Opcodes.ALOAD, 2)); // ItemStack
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", false));
        insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
        return insertHead(methodNode, insn);
    }

    function patchInventorySetItem(methodNode, hookName) {
        var insn = new InsnList();
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0)); // Inventory self
        insn.add(new VarInsnNode(Opcodes.ILOAD, 1)); // slot
        insn.add(new VarInsnNode(Opcodes.ALOAD, 2)); // ItemStack
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, "(Lnet/minecraft/world/entity/player/Inventory;ILnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", false));
        insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
        return insertHead(methodNode, insn);
    }

    function insertVoidCallBeforeReturns(methodNode, hookName, hookDesc, loaders) {
        var iterator = methodNode.instructions.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();
            if (node.getOpcode() === Opcodes.RETURN) {
                var insn = new InsnList();
                loaders(insn);
                insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, hookDesc, false));
                methodNode.instructions.insertBefore(node, insn);
            }
        }
        return methodNode;
    }

    function patchHeadVoidCancel(methodNode, hookName, hookDesc, loaders) {
        var insn = new InsnList();
        var cont = new LabelNode();

        loaders(insn);
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, hookDesc, false));
        insn.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        insn.add(new InsnNode(Opcodes.RETURN));
        insn.add(cont);

        return insertHead(methodNode, insn);
    }

    function patchHeadBooleanCancelFalse(methodNode, hookName, hookDesc, loaders) {
        var insn = new InsnList();
        var cont = new LabelNode();

        loaders(insn);
        insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, hookDesc, false));
        insn.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        insn.add(new InsnNode(Opcodes.ICONST_0));
        insn.add(new InsnNode(Opcodes.IRETURN));
        insn.add(cont);

        return insertHead(methodNode, insn);
    }

    function wrapObjectReturns(methodNode, hookName, hookDesc, loaders) {
        var iterator = methodNode.instructions.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();
            if (node.getOpcode() === Opcodes.ARETURN) {
                var insn = new InsnList();
                loaders(insn);
                insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, hookName, hookDesc, false));
                methodNode.instructions.insertBefore(node, insn);
            }
        }
        return methodNode;
    }

    return {
        "AA_LivingEntity_setHealth_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_21153_"), // setHealth
                "methodDesc": "(F)V"
            },
            "transformer": function(methodNode) {
                return patchFloatArg(methodNode, "trueDemonSetHealth");
            }
        },

        "AA_LivingEntity_heal_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_5634_"), // heal
                "methodDesc": "(F)V"
            },
            "transformer": function(methodNode) {
                return patchFloatArg(methodNode, "trueDemonHeal");
            }
        },

        "AA_LivingEntity_checkTotem_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_21262_"), // checkTotemDeathProtection
                "methodDesc": "(Lnet/minecraft/world/damagesource/DamageSource;)Z"
            },
            "transformer": function(methodNode) {
                return patchHeadBoolShortCircuit(
                    methodNode,
                    "shouldBlockTotem",
                    "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z",
                    Opcodes.ICONST_0,
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    }
                );
            }
        },

        "AA_ForgeHooks_onLivingDeath_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraftforge.common.ForgeHooks",
                "methodName": "onLivingDeath",
                "methodDesc": "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z"
            },
            "transformer": function(methodNode) {
                return wrapReturns(
                    methodNode,
                    Opcodes.IRETURN,
                    "trueDemonForgeLivingDeath",
                    "(ZLnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    }
                );
            }
        },

        "AA_ForgeHooks_onLivingAttack_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraftforge.common.ForgeHooks",
                "methodName": "onLivingAttack",
                "methodDesc": "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            },
            "transformer": function(methodNode) {
                return wrapReturns(
                    methodNode,
                    Opcodes.IRETURN,
                    "trueDemonForgeLivingAttack",
                    "(ZLnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        insn.add(new VarInsnNode(Opcodes.FLOAD, 2));
                    }
                );
            }
        },

        "AA_SynchedEntityData_set_fallback_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.network.syncher.SynchedEntityData",
                "methodName": ASMAPI.mapMethod("m_135381_"),
                "methodDesc": "(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V"
            },
            "transformer": function(methodNode) {
                var insn = new InsnList();

                insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insn.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/network/syncher/SynchedEntityData", ASMAPI.mapField("f_135344_"), "Lnet/minecraft/world/entity/Entity;"));
                insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insn.add(new VarInsnNode(Opcodes.ALOAD, 2));
                insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, core, "trueDemonDataSet", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)Ljava/lang/Object;", false));

                insn.add(new VarInsnNode(Opcodes.ASTORE, 2));
                methodNode.instructions.insert(insn);
                return methodNode;
            }
        },

        "AA_Player_setItemSlot_purge_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Player",
                "methodName": ASMAPI.mapMethod("m_8061_"), // setItemSlot
                "methodDesc": "(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"
            },
            "transformer": function(methodNode) {
                return patchPlayerSetItemSlot(methodNode, "trueDemonPlayerSetItemSlot");
            }
        },

        "AA_LivingEntity_setStackInHand_purge_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_21008_"), // setItemInHand
                "methodDesc": "(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"
            },
            "transformer": function(methodNode) {
                return patchLivingSetStackInHand(methodNode, "trueDemonLivingSetStackInHand");
            }
        },

        "AA_Inventory_setItem_purge_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_6836_"), // setItem
                "methodDesc": "(ILnet/minecraft/world/item/ItemStack;)V"
            },
            "transformer": function(methodNode) {
                return patchInventorySetItem(methodNode, "trueDemonInventorySetItem");
            }
        },

        "AA_Player_tick_purge_tail_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Player",
                "methodName": ASMAPI.mapMethod("m_8119_"), // tick
                "methodDesc": "()V"
            },
            "transformer": function(methodNode) {
                return insertVoidCallBeforeReturns(
                    methodNode,
                    "trueDemonPlayerTick",
                    "(Lnet/minecraft/world/entity/player/Player;)V",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    }
                );
            }
        },

        "AA_ServerPlayer_doTick_purge_tail_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.server.level.ServerPlayer",
                "methodName": ASMAPI.mapMethod("m_9240_"), // doTick
                "methodDesc": "()V"
            },
            "transformer": function(methodNode) {
                return insertVoidCallBeforeReturns(
                    methodNode,
                    "trueDemonServerPlayerPostTick",
                    "(Lnet/minecraft/server/level/ServerPlayer;)V",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    }
                );
            }
        },

        "AA_Inventory_add_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_36054_"), // add(ItemStack)
                "methodDesc": "(Lnet/minecraft/world/item/ItemStack;)Z"
            },
            "transformer": function(methodNode) {
                return patchHeadBooleanCancelFalse(
                    methodNode,
                    "trueDemonInventoryAdd",
                    "(Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    }
                );
            }
        },

        "AA_Inventory_addAt_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_36040_"), // add(int, ItemStack)
                "methodDesc": "(ILnet/minecraft/world/item/ItemStack;)Z"
            },
            "transformer": function(methodNode) {
                return patchHeadBooleanCancelFalse(
                    methodNode,
                    "trueDemonInventoryAddAt",
                    "(Lnet/minecraft/world/entity/player/Inventory;ILnet/minecraft/world/item/ItemStack;)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    }
                );
            }
        },

        "AA_Inventory_placeBack_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_150079_"), // placeItemBackInInventory(ItemStack)
                "methodDesc": "(Lnet/minecraft/world/item/ItemStack;)V"
            },
            "transformer": function(methodNode) {
                return patchHeadVoidCancel(
                    methodNode,
                    "trueDemonPlaceBack",
                    "(Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    }
                );
            }
        },

        "AA_Inventory_placeBackNotify_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_150076_"), // placeItemBackInInventory(ItemStack, boolean)
                "methodDesc": "(Lnet/minecraft/world/item/ItemStack;Z)V"
            },
            "transformer": function(methodNode) {
                return patchHeadVoidCancel(
                    methodNode,
                    "trueDemonPlaceBackNotify",
                    "(Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;Z)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        insn.add(new VarInsnNode(Opcodes.ILOAD, 2));
                    }
                );
            }
        },

        "AA_Inventory_setPickedItem_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_36012_"), // setPickedItem(ItemStack)
                "methodDesc": "(Lnet/minecraft/world/item/ItemStack;)V"
            },
            "transformer": function(methodNode) {
                return patchHeadVoidCancel(
                    methodNode,
                    "trueDemonSetPickedItem",
                    "(Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    }
                );
            }
        },

        "AA_Inventory_pickSlot_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_36038_"), // pickSlot(int)
                "methodDesc": "(I)V"
            },
            "transformer": function(methodNode) {
                return patchHeadVoidCancel(
                    methodNode,
                    "trueDemonPickSlot",
                    "(Lnet/minecraft/world/entity/player/Inventory;I)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    }
                );
            }
        },

        "AA_ItemEntity_playerTouch_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.item.ItemEntity",
                "methodName": ASMAPI.mapMethod("m_6123_"), // playerTouch(Player)
                "methodDesc": "(Lnet/minecraft/world/entity/player/Player;)V"
            },
            "transformer": function(methodNode) {
                return patchHeadVoidCancel(
                    methodNode,
                    "trueDemonItemEntityTouch",
                    "(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/entity/player/Player;)Z",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    }
                );
            }
        },

        "AA_LivingEntity_getItemInHand_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_21120_"), // getItemInHand(InteractionHand)
                "methodDesc": "(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"
            },
            "transformer": function(methodNode) {
                return wrapObjectReturns(
                    methodNode,
                    "trueDemonGetItemInHand",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    }
                );
            }
        },

        "AA_LivingEntity_getMainHandItem_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_21205_"), // getMainHandItem
                "methodDesc": "()Lnet/minecraft/world/item/ItemStack;"
            },
            "transformer": function(methodNode) {
                return wrapObjectReturns(
                    methodNode,
                    "trueDemonGetMainHandItem",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    }
                );
            }
        },

        "AA_LivingEntity_getOffhandItem_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_21206_"), // getOffhandItem
                "methodDesc": "()Lnet/minecraft/world/item/ItemStack;"
            },
            "transformer": function(methodNode) {
                return wrapObjectReturns(
                    methodNode,
                    "trueDemonGetOffhandItem",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    }
                );
            }
        },

        "AA_LivingEntity_getItemBySlot_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.LivingEntity",
                "methodName": ASMAPI.mapMethod("m_6844_"), // getItemBySlot
                "methodDesc": "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
            },
            "transformer": function(methodNode) {
                return wrapObjectReturns(
                    methodNode,
                    "trueDemonGetItemBySlot",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0)); // self
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 1)); // slot
                    }
                );
            }
        },

        "AA_Inventory_getArmor_hook": {
            "target": {
                "type": "METHOD",
                "class": "net.minecraft.world.entity.player.Inventory",
                "methodName": ASMAPI.mapMethod("m_36052_"), // getArmor
                "methodDesc": "(I)Lnet/minecraft/world/item/ItemStack;"
            },
            "transformer": function(methodNode) {
                return wrapObjectReturns(
                    methodNode,
                    "trueDemonInventoryGetArmor",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Inventory;I)Lnet/minecraft/world/item/ItemStack;",
                    function(insn) {
                        insn.add(new VarInsnNode(Opcodes.ALOAD, 0)); // inventory
                        insn.add(new VarInsnNode(Opcodes.ILOAD, 1)); // armor slot index
                    }
                );
            }
        },
    };
}
