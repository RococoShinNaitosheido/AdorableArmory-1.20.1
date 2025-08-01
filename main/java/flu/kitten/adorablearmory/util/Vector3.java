package flu.kitten.adorablearmory.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class Vector3 implements Copyable<Vector3> {

    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 CENTER = new Vector3(0.5, 0.5, 0.5);
    //@formatter:off
    public static final Vector3 ONE =   new Vector3( 1, 1, 1);
    public static final Vector3 X_POS = new Vector3( 1, 0, 0);
    public static final Vector3 X_NEG = new Vector3(-1, 0, 0);
    public static final Vector3 Y_POS = new Vector3( 0, 1, 0);
    public static final Vector3 Y_NEG = new Vector3( 0,-1, 0);
    public static final Vector3 Z_POS = new Vector3( 0, 0, 1);
    public static final Vector3 Z_NEG = new Vector3( 0, 0,-1);
    //@formatter:on

    public double x;
    public double y;
    public double z;

    public Vector3() {
    }

    public Vector3(double d, double d1, double d2) {
        x = d;
        y = d1;
        z = d2;
    }

    public Vector3(Vector3 vec) {
        x = vec.x;
        y = vec.y;
        z = vec.z;
    }

    public Vector3(double[] da) {
        this(da[0], da[1], da[2]);
    }

    public Vector3(float[] fa) {
        this(fa[0], fa[1], fa[2]);
    }

    public Vector3(Vec3 vec) {
        x = vec.x;
        y = vec.y;
        z = vec.z;
    }

    public static Vector3 fromBlockPos(BlockPos pos) {
        return fromVec3i(pos);
    }

    public static Vector3 fromVec3i(Vec3i pos) {
        return new Vector3(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vector3 fromBlockPosCenter(BlockPos pos) {
        return fromBlockPos(pos).add(0.5);
    }

    public static Vector3 fromEntity(Entity e) {
        return new Vector3(e.position());
    }

    public static Vector3 fromEntityCenter(Entity e) {
        return new Vector3(e.position()).add(0, e.getMyRidingOffset() + e.getBbHeight() / 2, 0);
    }

    public static Vector3 fromTile(BlockEntity tile) {
        return fromBlockPos(tile.getBlockPos());
    }

    public static Vector3 fromTileCenter(BlockEntity tile) {
        return fromTile(tile).add(0.5);
    }

    public static Vector3 fromAxes(double[] da) {
        return new Vector3(da[2], da[0], da[1]);
    }

    public static Vector3 fromAxes(float[] fa) {
        return new Vector3(fa[2], fa[0], fa[1]);
    }

    public static Vector3 fromArray(double[] da) {
        return new Vector3(da[0], da[1], da[2]);
    }

    public static Vector3 fromArray(float[] fa) {
        return new Vector3(fa[0], fa[1], fa[2]);
    }

    public static Vector3 fromNBT(CompoundTag tag) {
        return new Vector3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }

    public Vec3 vec3() {
        return new Vec3(x, y, z);
    }

    public BlockPos pos() {
        return new BlockPos(MathUtils.floor(x), MathUtils.floor(y), MathUtils.floor(z));
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        return tag;
    }

    public Vector3f vector3f() {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    public Vector4f vector4f() {
        return new Vector4f((float) x, (float) y, (float) z, 1);
    }

    public double[] toArrayD() {
        return new double[]{x, y, z};
    }

    public float[] toArrayF() {
        return new float[]{(float) x, (float) y, (float) z};
    }

    public Vector3 set(double x1, double y1, double z1) {
        x = x1;
        y = y1;
        z = z1;
        return this;
    }

    public Vector3 set(double d) {
        return set(d, d, d);
    }

    public Vector3 set(Vector3 vec) {
        return set(vec.x, vec.y, vec.z);
    }

    public Vector3 set(Vec3i vec) {
        return set(vec.getX(), vec.getY(), vec.getZ());
    }

    public Vector3 set(double[] da) {
        return set(da[0], da[1], da[2]);
    }

    public Vector3 set(float[] fa) {
        return set(fa[0], fa[1], fa[2]);
    }

    public Vector3 add(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
        return this;
    }

    public Vector3 add(double d) {
        return add(d, d, d);
    }

    public Vector3 add(Vector3 vec) {
        return add(vec.x, vec.y, vec.z);
    }

    public Vector3 add(Vec3 vec) {
        return add(vec.x, vec.y, vec.z);
    }

    // TODO Move to use Vec3i
    public Vector3 add(BlockPos pos) {
        return add(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vector3 subtract(double dx, double dy, double dz) {
        x -= dx;
        y -= dy;
        z -= dz;
        return this;
    }

    public Vector3 subtract(double d) {
        return subtract(d, d, d);
    }

    public Vector3 subtract(Vector3 vec) {
        return subtract(vec.x, vec.y, vec.z);
    }

    public Vector3 subtract(Vec3 vec) {
        return subtract(vec.x, vec.y, vec.z);
    }

    public Vector3 subtract(BlockPos pos) {
        return subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vector3 multiply(double fx, double fy, double fz) {
        x *= fx;
        y *= fy;
        z *= fz;
        return this;
    }

    public Vector3 multiply(double f) {
        return multiply(f, f, f);
    }

    public Vector3 multiply(Vector3 f) {
        return multiply(f.x, f.y, f.z);
    }

    public Vector3 divide(double fx, double fy, double fz) {
        x /= fx;
        y /= fy;
        z /= fz;
        return this;
    }

    public Vector3 divide(double f) {
        return divide(f, f, f);
    }

    public Vector3 divide(Vector3 vec) {
        return divide(vec.x, vec.y, vec.z);
    }

    public Vector3 divide(BlockPos pos) {
        return divide(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vector3 floor() {
        x = MathUtils.floor(x);
        y = MathUtils.floor(y);
        z = MathUtils.floor(z);
        return this;
    }

    public Vector3 ceil() {
        x = MathUtils.ceil(x);
        y = MathUtils.ceil(y);
        z = MathUtils.ceil(z);
        return this;
    }

    public double mag() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double magSquared() {
        return x * x + y * y + z * z;
    }

    public Vector3 negate() {
        x = -x;
        y = -y;
        z = -z;
        return this;
    }

    public Vector3 normalize() {
        double d = mag();
        if (d != 0) {
            multiply(1 / d);
        }
        return this;
    }

    public double distance(Vector3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceSquared(Vector3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;

        return dx * dx + dy * dy + dz * dz;
    }

    public double dotProduct(double x1, double y1, double z1) {
        return x1 * x + y1 * y + z1 * z;
    }

    public double dotProduct(Vector3 vec) {
        double d = vec.x * x + vec.y * y + vec.z * z;

        if (d > 1 && d < 1.00001) {
            d = 1;
        } else if (d < -1 && d > -1.00001) {
            d = -1;
        }
        return d;
    }

    public Vector3 crossProduct(Vector3 vec) {
        double d = y * vec.z - z * vec.y;
        double d1 = z * vec.x - x * vec.z;
        double d2 = x * vec.y - y * vec.x;
        x = d;
        y = d1;
        z = d2;
        return this;
    }

    public Vector3 perpendicular() {
        if (z == 0) {
            return zCrossProduct();
        }
        return xCrossProduct();
    }

    public Vector3 xCrossProduct() {
        double d = z;
        double d1 = -y;
        x = 0;
        y = d;
        z = d1;
        return this;
    }

    public Vector3 zCrossProduct() {
        double d = y;
        double d1 = -x;
        x = d;
        y = d1;
        z = 0;
        return this;
    }

    public Vector3 yCrossProduct() {
        double d = -z;
        double d1 = x;
        x = d;
        y = 0;
        z = d1;
        return this;
    }

    public double scalarProject(Vector3 b) {
        double l = b.mag();
        return l == 0 ? 0 : dotProduct(b) / l;
    }

    public Vector3 project(Vector3 b) {
        double l = b.magSquared();
        if (l == 0) {
            set(0, 0, 0);
            return this;
        }
        double m = dotProduct(b) / l;
        set(b).multiply(m);
        return this;
    }

    public double angle(Vector3 vec) {
        return Math.acos(copy().normalize().dotProduct(vec.copy().normalize()));
    }

    @Nullable
    public Vector3 YZintercept(Vector3 end, double px) {
        double dx = end.x - x;
        double dy = end.y - y;
        double dz = end.z - z;

        if (dx == 0) {
            return null;
        }

        double d = (px - x) / dx;
        if (MathUtils.between(-1E-5, d, 1E-5)) {
            return this;
        }

        if (!MathUtils.between(0, d, 1)) {
            return null;
        }

        x = px;
        y += d * dy;
        z += d * dz;
        return this;
    }

    @Nullable
    public Vector3 XZintercept(Vector3 end, double py) {
        double dx = end.x - x;
        double dy = end.y - y;
        double dz = end.z - z;

        if (dy == 0) {
            return null;
        }

        double d = (py - y) / dy;
        if (MathUtils.between(-1E-5, d, 1E-5)) {
            return this;
        }

        if (!MathUtils.between(0, d, 1)) {
            return null;
        }

        x += d * dx;
        y = py;
        z += d * dz;
        return this;
    }

    @Nullable
    public Vector3 XYintercept(Vector3 end, double pz) {
        double dx = end.x - x;
        double dy = end.y - y;
        double dz = end.z - z;

        if (dz == 0) {
            return null;
        }

        double d = (pz - z) / dz;
        if (MathUtils.between(-1E-5, d, 1E-5)) {
            return this;
        }

        if (!MathUtils.between(0, d, 1)) {
            return null;
        }

        x += d * dx;
        y += d * dy;
        z = pz;
        return this;
    }

    public boolean isZero() {
        return x == 0 && y == 0 && z == 0;
    }

    public boolean isAxial() {
        return x == 0 ? (y == 0 || z == 0) : (y == 0 && z == 0);
    }

    public double getSide(int side) {
        switch (side) {
            case 0:
            case 1:
                return y;
            case 2:
            case 3:
                return z;
            case 4:
            case 5:
                return x;
        }
        throw new IndexOutOfBoundsException("Switch Falloff");
    }

    public Vector3 setSide(int s, double v) {
        switch (s) {
            case 0, 1 -> y = v;
            case 2, 3 -> z = v;
            case 4, 5 -> x = v;
            default -> throw new IndexOutOfBoundsException("Switch Falloff");
        }
        return this;
    }

    @Override
    public int hashCode() {
        long j = Double.doubleToLongBits(x);
        int i = (int) (j ^ j >>> 32);
        j = Double.doubleToLongBits(y);
        i = 31 * i + (int) (j ^ j >>> 32);
        j = Double.doubleToLongBits(z);
        i = 31 * i + (int) (j ^ j >>> 32);
        return i;
    }

    @Override
    public boolean equals(Object o) {
        if (super.equals(o)) {
            return true;
        }
        if (!(o instanceof Vector3)) {
            return false;
        }
        Vector3 v = (Vector3) o;
        return x == v.x && y == v.y && z == v.z;
    }

    /**
     * Equals method with tolerance
     *
     * @return true if this is equal to v within +-1E-5
     */
    public boolean equalsT(Vector3 v) {
        return MathUtils.between(x - 1E-5, v.x, x + 1E-5) && MathUtils.between(y - 1E-5, v.y, y + 1E-5) && MathUtils.between(z - 1E-5, v.z, z + 1E-5);
    }

    @Override
    public Vector3 copy() {
        return new Vector3(this);
    }

    @Override
    public String toString() {
        MathContext cont = new MathContext(4, RoundingMode.HALF_UP);
        return "Vector3(" + new BigDecimal(x, cont) + ", " + new BigDecimal(y, cont) + ", " + new BigDecimal(z, cont) + ")";
    }

    public Vector3 $tilde() {
        return normalize();
    }

    public Vector3 unary_$tilde() {
        return normalize();
    }

    public Vector3 $plus(Vector3 v) {
        return add(v);
    }

    public Vector3 $minus(Vector3 v) {
        return subtract(v);
    }

    public Vector3 $times(double d) {
        return multiply(d);
    }

    public Vector3 $div(double d) {
        return multiply(1 / d);
    }

    public Vector3 $times(Vector3 v) {
        return crossProduct(v);
    }

    public double $dot$times(Vector3 v) {
        return dotProduct(v);
    }
}
