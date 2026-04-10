package flu.kitten.adorablearmory.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class Vector3 implements Copyable<Vector3> {

    public double x;
    public double y;
    public double z;

    public Vector3() {
    }

    public Vector3(Vector3 vec) {
        x = vec.x;
        y = vec.y;
        z = vec.z;
    }

    public Vec3 vec3() {
        return new Vec3(x, y, z);
    }

    public BlockPos pos() {
        return new BlockPos(MathUtils.floor(x), MathUtils.floor(y), MathUtils.floor(z));
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

    public Vector3 add(BlockPos pos) {
        return add(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vector3 subtract(double dx, double dy, double dz) {
        x -= dx;
        y -= dy;
        z -= dz;
        return this;
    }

    public Vector3 subtract(Vector3 vec) {
        return subtract(vec.x, vec.y, vec.z);
    }

    public void multiply(double fx, double fy, double fz) {
        x *= fx;
        y *= fy;
        z *= fz;
    }

    public void multiply(double f) {
        multiply(f, f, f);
    }

    public Vector3 floor() {
        x = MathUtils.floor(x);
        y = MathUtils.floor(y);
        z = MathUtils.floor(z);
        return this;
    }

    public double mag() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3 normalize() {
        double d = mag();
        if (d != 0) {
            multiply(1 / d);
        }
        return this;
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
        if (!(o instanceof Vector3 v)) {
            return false;
        }
        return x == v.x && y == v.y && z == v.z;
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
}
