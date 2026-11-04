package com.hypixel.hytale.math.vector;

import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.joml.Vector3ic;

/**
 * Compatibility shim.
 *
 * <p>Hytale replaced its own {@code com.hypixel.hytale.math.vector} value types with JOML. The
 * upstream HyProTech sources still target the old types, so this class restores the old surface as a
 * thin subclass of {@link org.joml.Vector3i}. Because it <em>is</em> a JOML vector, it can still be
 * handed to current engine methods that expect JOML types.</p>
 */
public class Vector3i extends org.joml.Vector3i {

    public static final Vector3i ZERO = new Vector3i(0, 0, 0);
    public static final Vector3i ONE = new Vector3i(1, 1, 1);
    public static final Vector3i UNIT_X = new Vector3i(1, 0, 0);
    public static final Vector3i UNIT_Y = new Vector3i(0, 1, 0);
    public static final Vector3i UNIT_Z = new Vector3i(0, 0, 1);
    public static final Vector3i MIN = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    public static final Vector3i MAX = new Vector3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    public Vector3i() {
        super();
    }

    public Vector3i(int x, int y, int z) {
        super(x, y, z);
    }

    public Vector3i(Vector3ic v) {
        super(v);
    }

    public Vector3i(Vector3dc v) {
        super((int) v.x(), (int) v.y(), (int) v.z());
    }

    public Vector3i(Vector3fc v) {
        super((int) v.x(), (int) v.y(), (int) v.z());
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Vector3i setX(int x) {
        this.x = x;
        return this;
    }

    public Vector3i setY(int y) {
        this.y = y;
        return this;
    }

    public Vector3i setZ(int z) {
        this.z = z;
        return this;
    }

    public Vector3i set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vector3i set(Vector3ic v) {
        this.x = v.x();
        this.y = v.y();
        this.z = v.z();
        return this;
    }

    public Vector3i copy() {
        return new Vector3i(this);
    }

    @Override
    public Vector3i clone() {
        return new Vector3i(this);
    }

    @Override
    public Vector3i add(Vector3ic v) {
        super.add(v);
        return this;
    }

    /**
     * Legacy Hytale signature: sets {@code this = a + b}. Not a JOML override (JOML's two-arg form is
     * {@code add(Vector3ic, Vector3i dest)}, which writes into a destination instead).
     */
    public Vector3i add(Vector3ic a, Vector3ic b) {
        this.x = a.x() + b.x();
        this.y = a.y() + b.y();
        this.z = a.z() + b.z();
        return this;
    }

    @Override
    public Vector3i add(int x, int y, int z) {
        super.add(x, y, z);
        return this;
    }

    @Override
    public Vector3i sub(Vector3ic v) {
        super.sub(v);
        return this;
    }

    /**
     * Legacy Hytale signature: sets {@code this = a - b}. Not a JOML override (JOML's two-arg form is
     * {@code sub(Vector3ic, Vector3i dest)}).
     */
    public Vector3i sub(Vector3ic a, Vector3ic b) {
        this.x = a.x() - b.x();
        this.y = a.y() - b.y();
        this.z = a.z() - b.z();
        return this;
    }

    @Override
    public Vector3i sub(int x, int y, int z) {
        super.sub(x, y, z);
        return this;
    }

    @Override
    public Vector3i mul(int scalar) {
        super.mul(scalar);
        return this;
    }

    @Override
    public Vector3i mul(Vector3ic v) {
        super.mul(v);
        return this;
    }

    @Override
    public Vector3i div(int scalar) {
        super.div(scalar);
        return this;
    }

    @Override
    public Vector3i negate() {
        super.negate();
        return this;
    }

    @Override
    public Vector3i zero() {
        super.zero();
        return this;
    }

    @Override
    public Vector3i min(Vector3ic v) {
        super.min(v);
        return this;
    }

    @Override
    public Vector3i max(Vector3ic v) {
        super.max(v);
        return this;
    }

    /**
     * Legacy Hytale signature: linear interpolation from {@code this} toward {@code other} by
     * {@code t}. JOML integer vectors have no {@code lerp}, so the float interpolation is rounded
     * back to int here.
     */
    public Vector3i lerp(Vector3ic other, float t) {
        this.x = (int) (x + (other.x() - x) * t);
        this.y = (int) (y + (other.y() - y) * t);
        this.z = (int) (z + (other.z() - z) * t);
        return this;
    }

    public double distanceTo(Vector3ic v) {
        return Math.sqrt(distanceSquared(v));
    }

    public long distanceSquared(Vector3ic v) {
        long dx = (long) x - v.x();
        long dy = (long) y - v.y();
        long dz = (long) z - v.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public double length() {
        return Math.sqrt((double) x * x + (double) y * y + (double) z * z);
    }

    @Override
    public String toString() {
        return "Vector3i(" + x + ", " + y + ", " + z + ")";
    }
}
