package flu.kitten.adorablearmory.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

public class MathUtils {

    public static final double pi = Math.PI;
    public static double[] SIN_TABLE = new double[65536];

    static {
        for (int i = 0; i < 65536; ++i) {
            SIN_TABLE[i] = Math.sin(i / 65536D * 2 * Math.PI);
        }
    }

    public static double sin(double d) {
        return SIN_TABLE[(int) ((float) d * 10430.378F) & 65535];
    }

    public static double cos(double d) {
        return SIN_TABLE[(int) ((float) d * 10430.378F + 16384.0F) & 65535];
    }

    public static double map(double valueIn, double inMin, double inMax, double outMin, double outMax) {
        return (valueIn - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
    }

    public static float map(float valueIn, float inMin, float inMax, float outMin, float outMax) {
        return (valueIn - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
    }

    public static int floor(double d) {
        int i = (int) d;
        return d < (double) i ? i - 1 : i;
    }

    public static int floor(float f) {
        int i = (int) f;
        return f < (float) i ? i - 1 : i;
    }

    public static float sqrt(float f) {
        return (float) Math.sqrt(f);
    }

    public static float sqrt(double f) {
        return (float) Math.sqrt(f);
    }

    public static BlockPos min(Vec3i pos1, Vec3i pos2) {
        return new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
    }

    public static BlockPos max(Vec3i pos1, Vec3i pos2) {
        return new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
    }

    public static boolean isAxial(BlockPos pos) {
        return pos.getX() == 0 ? (pos.getY() == 0 || pos.getZ() == 0) : (pos.getY() == 0 && pos.getZ() == 0);
    }

    public static int toSide(BlockPos pos) {
        Direction side = getSide(pos);
        return side == null ? -1 : side.get3DDataValue();
    }

    public static Direction getSide(BlockPos pos) {
        if (!isAxial(pos)) {
            return null;
        }
        if (pos.getY() < 0) {
            return Direction.DOWN;
        }
        if (pos.getY() > 0) {
            return Direction.UP;
        }
        if (pos.getZ() < 0) {
            return Direction.NORTH;
        }
        if (pos.getZ() > 0) {
            return Direction.SOUTH;
        }
        if (pos.getX() < 0) {
            return Direction.WEST;
        }
        if (pos.getX() > 0) {
            return Direction.EAST;
        }

        return null;
    }
}
