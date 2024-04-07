package net.nothingtv.game.network;

public class NVector3 {
    public float x, y, z;

    public void set(NVector3 other) {
        x = other.x;
        y = other.y;
        z = other.z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return String.format("[%.3f,%.3f,%.3f]", x, y, z);
    }
}
