package cn.nukkit.network.protocol;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * author: MagicDroidX & iNevet
 * Nukkit Project
 */
public interface ProtocolInfo {

    @ApiStatus.AvailableSince("0.13.0")
    int v0_13_0 = 37;
    @ApiStatus.AvailableSince("0.13.1")
    int v0_13_1 = 38;
    @ApiStatus.AvailableSince("0.13.2")
    int v0_13_2 = 39;
    @ApiStatus.AvailableSince("0.14.0")
    int v0_14_0 = 45;
    @ApiStatus.AvailableSince("0.14.1")
    int v0_14_1 = 46;
    @ApiStatus.AvailableSince("0.14.2")
    int v0_14_2 = 60;
    @ApiStatus.AvailableSince("0.14.3")
    int v0_14_3 = 70;
    @ApiStatus.AvailableSince("0.15.0")
    int v0_15_0 = 81;
    @ApiStatus.AvailableSince("0.15.4")
    int v0_15_4 = 82;
    @ApiStatus.AvailableSince("0.15.9")
    int v0_15_9 = 83;
    @ApiStatus.AvailableSince("0.15.10")
    int v0_15_10 = 84;
    @ApiStatus.AvailableSince("0.16.0")
    int v0_16_0 = 91;
    @ApiStatus.AvailableSince("1.0.0.0")
    int v1_0_0_0 = 92;
    @ApiStatus.AvailableSince("1.0.0")
    int v1_0_0 = 100;
    @ApiStatus.AvailableSince("1.0.3")
    int v1_0_3 = 101;
    @ApiStatus.AvailableSince("1.0.4")
    int v1_0_4 = 102;
    @ApiStatus.AvailableSince("1.0.5")
    int v1_0_5 = 105;
    @ApiStatus.AvailableSince("1.0.6")
    int v1_0_6 = 106;
    @ApiStatus.AvailableSince("1.0.7")
    int v1_0_7 = 107;
    @ApiStatus.AvailableSince("1.0.9")
    int v1_0_9 = v1_0_7; // Alias, same protocol number
    @ApiStatus.AvailableSince("1.1.0")
    int v1_1_0 = 113;

    /**
     * 当前分支只维护 1.1.x 及以下，不引入 1.2+ 协议。
     */
    @SupportedProtocol
    int CURRENT_PROTOCOL = v1_1_0;

    List<Integer> SUPPORTED_PROTOCOLS = Collections.unmodifiableList(Arrays.asList(
            v0_13_0,
            v0_13_1,
            v0_13_2,
            v0_14_0,
            v0_14_1,
            v0_14_2,
            v0_14_3,
            v0_15_0,
            v0_15_4,
            v0_15_9,
            v0_15_10,
            v0_16_0,
            v1_0_0_0,
            v1_0_0,
            v1_0_3,
            v1_0_4,
            v1_0_5,
            v1_0_6,
            v1_0_7,
            v1_1_0
    ));

    String MINECRAFT_VERSION_NETWORK = getMinecraftVersion(CURRENT_PROTOCOL);
    String MINECRAFT_VERSION = "v" + MINECRAFT_VERSION_NETWORK;

    @SupportedProtocol
    static int getPacketPoolProtocol(int protocol) {
        switch (protocol) {
            case v0_13_0:
            case v0_13_1:
            case v0_13_2:
                return v0_13_2;
            case v0_14_0:
            case v0_14_1:
            case v0_14_2:
            case v0_14_3:
                return v0_14_2;
            case v0_15_0:
                return v0_15_0;
            case v0_15_4:
            case v0_15_9:
            case v0_15_10:
                return v0_15_10;
            case v0_16_0:
                return v0_16_0;
            case v1_0_0_0:
                return v1_0_0_0;
            case v1_0_0:
                return v1_0_0;
            case v1_0_3:
                return v1_0_3;
            case v1_0_4:
                return v1_0_4;
            case v1_0_5:
            case v1_0_6:
            case v1_0_7:
                return v1_0_5;
            case v1_1_0:
            default:
                return v1_1_0;
        }
    }

    /**
     * 标记一个实现从哪个协议版本开始可用。
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
    @interface SinceProtocol {

        @SupportedProtocol
        int value();
    }

    /**
     * 标记一个实现从哪个后续协议开始不可再用。
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
    @interface UnsupportedSince {

        @SupportedProtocol
        int value();
    }

    byte LOGIN_PACKET = 0x01;
    byte PLAY_STATUS_PACKET = 0x02;
    byte SERVER_TO_CLIENT_HANDSHAKE_PACKET = 0x03;
    byte CLIENT_TO_SERVER_HANDSHAKE_PACKET = 0x04;
    byte DISCONNECT_PACKET = 0x05;
    byte RESOURCE_PACKS_INFO_PACKET = 0x06;
    byte RESOURCE_PACK_STACK_PACKET = 0x07;
    byte RESOURCE_PACK_CLIENT_RESPONSE_PACKET = 0x08;
    byte TEXT_PACKET = 0x09;
    byte SET_TIME_PACKET = 0x0a;
    byte START_GAME_PACKET = 0x0b;
    byte ADD_PLAYER_PACKET = 0x0c;
    byte ADD_ENTITY_PACKET = 0x0d;
    byte REMOVE_ENTITY_PACKET = 0x0e;
    byte ADD_ITEM_ENTITY_PACKET = 0x0f;
    byte ADD_HANGING_ENTITY_PACKET = 0x10;
    byte TAKE_ITEM_ENTITY_PACKET = 0x11;
    byte MOVE_ENTITY_PACKET = 0x12;
    byte MOVE_PLAYER_PACKET = 0x13;
    byte RIDER_JUMP_PACKET = 0x14;
    byte REMOVE_BLOCK_PACKET = 0x15;
    byte UPDATE_BLOCK_PACKET = 0x16;
    byte ADD_PAINTING_PACKET = 0x17;
    byte EXPLODE_PACKET = 0x18;
    byte LEVEL_SOUND_EVENT_PACKET = 0x19;
    byte LEVEL_EVENT_PACKET = 0x1a;
    byte BLOCK_EVENT_PACKET = 0x1b;
    byte ENTITY_EVENT_PACKET = 0x1c;
    byte MOB_EFFECT_PACKET = 0x1d;
    byte UPDATE_ATTRIBUTES_PACKET = 0x1e;
    byte MOB_EQUIPMENT_PACKET = 0x1f;
    byte MOB_ARMOR_EQUIPMENT_PACKET = 0x20;
    byte INTERACT_PACKET = 0x21;
    byte BLOCK_PICK_REQUEST_PACKET = 0x22;
    byte USE_ITEM_PACKET = 0x23;
    byte PLAYER_ACTION_PACKET = 0x24;
    byte ENTITY_FALL_PACKET = 0x25;
    byte HURT_ARMOR_PACKET = 0x26;
    byte SET_ENTITY_DATA_PACKET = 0x27;
    byte SET_ENTITY_MOTION_PACKET = 0x28;
    byte SET_ENTITY_LINK_PACKET = 0x29;
    byte SET_HEALTH_PACKET = 0x2a;
    byte SET_SPAWN_POSITION_PACKET = 0x2b;
    byte ANIMATE_PACKET = 0x2c;
    byte RESPAWN_PACKET = 0x2d;
    byte DROP_ITEM_PACKET = 0x2e;
    byte INVENTORY_ACTION_PACKET = 0x2f;
    byte CONTAINER_OPEN_PACKET = 0x30;
    byte CONTAINER_CLOSE_PACKET = 0x31;
    byte CONTAINER_SET_SLOT_PACKET = 0x32;
    byte CONTAINER_SET_DATA_PACKET = 0x33;
    byte CONTAINER_SET_CONTENT_PACKET = 0x34;
    byte CRAFTING_DATA_PACKET = 0x35;
    byte CRAFTING_EVENT_PACKET = 0x36;
    byte ADVENTURE_SETTINGS_PACKET = 0x37;
    byte BLOCK_ENTITY_DATA_PACKET = 0x38;
    byte PLAYER_INPUT_PACKET = 0x39;
    byte FULL_CHUNK_DATA_PACKET = 0x3a;
    byte SET_COMMANDS_ENABLED_PACKET = 0x3b;
    byte SET_DIFFICULTY_PACKET = 0x3c;
    byte CHANGE_DIMENSION_PACKET = 0x3d;
    byte SET_PLAYER_GAME_TYPE_PACKET = 0x3e;
    byte PLAYER_LIST_PACKET = 0x3f;
    byte SIMPLE_EVENT_PACKET = 0x40;
    byte EVENT_PACKET = 0x41;
    byte SPAWN_EXPERIENCE_ORB_PACKET = 0x42;
    byte CLIENTBOUND_MAP_ITEM_DATA_PACKET = 0x43;
    byte MAP_INFO_REQUEST_PACKET = 0x44;
    byte REQUEST_CHUNK_RADIUS_PACKET = 0x45;
    byte CHUNK_RADIUS_UPDATED_PACKET = 0x46;
    byte ITEM_FRAME_DROP_ITEM_PACKET = 0x47;
    byte REPLACE_ITEM_IN_SLOT_PACKET = 0x48;
    byte GAME_RULES_CHANGED_PACKET = 0x49;
    byte CAMERA_PACKET = 0x4a;
    byte ADD_ITEM_PACKET = 0x4b;
    byte BOSS_EVENT_PACKET = 0x4c;
    byte SHOW_CREDITS_PACKET = 0x4d;
    byte AVAILABLE_COMMANDS_PACKET = 0x4e;
    byte COMMAND_STEP_PACKET = 0x4f;
    byte COMMAND_BLOCK_UPDATE_PACKET = 0x50;
    byte UPDATE_TRADE_PACKET = 0x51;
    byte UPDATE_EQUIP_PACKET = 0x52;
    byte RESOURCE_PACK_DATA_INFO_PACKET = 0x53;
    byte RESOURCE_PACK_CHUNK_DATA_PACKET = 0x54;
    byte RESOURCE_PACK_CHUNK_REQUEST_PACKET = 0x55;
    byte TRANSFER_PACKET = 0x56;
    byte PLAY_SOUND_PACKET = 0x57;
    byte STOP_SOUND_PACKET = 0x58;
    byte SET_TITLE_PACKET = 0x59;
    byte ADD_BEHAVIOR_TREE_PACKET = 0x5a;
    byte STRUCTURE_BLOCK_UPDATE_PACKET = 0x5b;
    byte BATCH_PACKET = (byte) 0xff;

    static boolean isSupportedProtocol(int protocol) {
        return SUPPORTED_PROTOCOLS.contains(protocol);
    }

    static boolean isLegacyProtocol(int protocol) {
        return protocol < v1_1_0;
    }

    static boolean isBefore0160(int protocol) {
        return protocol < v0_16_0;
    }

    static String getMinecraftVersion(@SupportedProtocol int protocol) {
        switch (protocol) {
            case v0_13_0:
                return "0.13.0";
            case v0_13_1:
                return "0.13.1";
            case v0_13_2:
                return "0.13.2";
            case v0_14_0:
                return "0.14.0";
            case v0_14_1:
                return "0.14.1";
            case v0_14_2:
                return "0.14.2";
            case v0_14_3:
                return "0.14.3";
            case v0_15_0:
                return "0.15.0";
            case v0_15_4:
                return "0.15.4";
            case v0_15_9:
                return "0.15.9";
            case v0_15_10:
                return "0.15.10";
            case v0_16_0:
                return "0.16.0";
            case v1_0_0_0:
                return "1.0.0.0";
            case v1_0_0:
                return "1.0.0";
            case v1_0_3:
                return "1.0.3";
            case v1_0_4:
                return "1.0.4";
            case v1_0_5:
                return "1.0.5";
            case v1_0_6:
                return "1.0.6";
            case v1_0_7:
                return "1.0.9"; // 1.0.7, 1.0.8, and 1.0.9 share protocol 107
            case v1_1_0:
            default:
                return "1.1.3";
        }
    }

    /**
     * 标记当前分支明确支持的协议版本常量。
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD})
    @MagicConstant(intValues = {
            v0_13_0,
            v0_13_1,
            v0_13_2,
            v0_14_0,
            v0_14_1,
            v0_14_2,
            v0_14_3,
            v0_15_0,
            v0_15_4,
            v0_15_9,
            v0_15_10,
            v0_16_0,
            v1_0_0_0,
            v1_0_0,
            v1_0_3,
            v1_0_4,
            v1_0_5,
            v1_0_6,
            v1_0_7,
            v1_1_0
    })
    @interface SupportedProtocol {
    }
}
