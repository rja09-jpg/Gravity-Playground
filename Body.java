import java.awt.Color;
import java.util.ArrayList;

/**
 * One object in the universe: a position, a velocity, an acceleration, and a mass.
 * Everything the physics needs to know about a single planet or star lives here.
 */
public class Body {
    public static final int MAX_TRAIL = 250;

    // position, velocity, acceleration (pixels and "time units")
    public double x, y;
    public double vx, vy;
    public double ax, ay;
    // acceleration from the previous step (needed by Velocity Verlet)
    public double prevAx, prevAy;

    public double mass;
    public double radius;
    public Color color;
    public ArrayList<double[]> trail = new ArrayList<double[]>();

    public Body(double x, double y, double vx, double vy, double mass, Color color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.mass = mass;
        this.color = color;
        this.radius = radiusFor(mass);
    }

    /** Bigger mass -> bigger circle (cube root, like real volume). */
    public static double radiusFor(double mass) {
        return 1.5 * Math.cbrt(mass) + 1.5;
    }

    public double kineticEnergy() {
        return 0.5 * mass * (vx * vx + vy * vy);
    }

    public void recordTrail() {
        trail.add(new double[] { x, y });
        if (trail.size() > MAX_TRAIL) {
            trail.remove(0);
        }
    }
}
