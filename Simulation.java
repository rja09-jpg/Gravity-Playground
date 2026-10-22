import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;

/**
 * The physics engine. No graphics in here, just math.
 *
 * Newton's law of gravity (with a tiny "softening" term so things don't
 * explode when two bodies get very close):
 *
 *     a_i = sum over j of   G * m_j * (r_j - r_i) / (|r_j - r_i|^2 + eps^2)^(3/2)
 *
 * Then each time step we nudge velocity by acceleration and position by velocity.
 */
public class Simulation {
    public static final double G = 1.0;

    private ArrayList<Body> bodies = new ArrayList<Body>();
    private double dt = 0.25;          // size of one time step
    private double softening = 3.0;    // epsilon
    private boolean useVerlet = true;  // false = naive Euler
    private boolean mergeEnabled = true;

    private double time = 0;
    private int escaped = 0;
    private int merged = 0;

    private boolean accelValid = false;    // do the bodies' ax, ay match their positions?
    private boolean baselineValid = false;  // have we recorded a starting energy?
    private double baselineEnergy = 0;

    private Random rand = new Random();

    // ------------------------------------------------------------------
    // getters and setters
    // ------------------------------------------------------------------
    public ArrayList<Body> getBodies() { return bodies; }
    public double getDt() { return dt; }
    public void setDt(double dt) { this.dt = dt; }
    public double getSoftening() { return softening; }
    public void setSoftening(double softening) { this.softening = softening; accelValid = false; baselineValid = false; }
    public boolean isUseVerlet() { return useVerlet; }
    public void setUseVerlet(boolean useVerlet) { this.useVerlet = useVerlet; accelValid = false; baselineValid = false; }
    public boolean isMergeEnabled() { return mergeEnabled; }
    public void setMergeEnabled(boolean mergeEnabled) { this.mergeEnabled = mergeEnabled; }
    public double getTime() { return time; }
    public int getEscaped() { return escaped; }
    public int getMerged() { return merged; }

    // ------------------------------------------------------------------
    // adding and clearing bodies
    // ------------------------------------------------------------------
    public Body addBody(double x, double y, double vx, double vy, double mass, Color color) {
        Body b = new Body(x, y, vx, vy, mass, color);
        bodies.add(b);
        accelValid = false;
        baselineValid = false;
        return b;
    }

    public void clear() {
        bodies.clear();
        time = 0;
        escaped = 0;
        merged = 0;
        accelValid = false;
        baselineValid = false;
    }

    // ------------------------------------------------------------------
    // the physics
    // ------------------------------------------------------------------

    /** Fill in ax and ay for every body in the list. */
    private void computeAccelerations(ArrayList<Body> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).ax = 0;
            list.get(i).ay = 0;
        }
        double eps2 = softening * softening;
        // look at every PAIR once, and apply the force to both bodies
        for (int i = 0; i < list.size(); i++) {
            Body a = list.get(i);
            for (int j = i + 1; j < list.size(); j++) {
                Body b = list.get(j);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double d2 = dx * dx + dy * dy + eps2;
                double inv = G / (d2 * Math.sqrt(d2));
                a.ax += dx * inv * b.mass;
                a.ay += dy * inv * b.mass;
                b.ax -= dx * inv * a.mass;
                b.ay -= dy * inv * a.mass;
            }
        }
    }

    /** Naive Euler: move using the OLD velocity, then update velocity. Drifts and spirals outward! */
    private void eulerStep(ArrayList<Body> list, double h) {
        computeAccelerations(list);
        for (Body b : list) {
            b.x += b.vx * h;
            b.y += b.vy * h;
            b.vx += b.ax * h;
            b.vy += b.ay * h;
        }
    }

    /** Velocity Verlet: much better at conserving energy, so orbits stay orbits. */
    private void verletStep(ArrayList<Body> list, double h) {
        for (Body b : list) {
            b.x += b.vx * h + 0.5 * b.ax * h * h;
            b.y += b.vy * h + 0.5 * b.ay * h * h;
            b.prevAx = b.ax;
            b.prevAy = b.ay;
        }
        computeAccelerations(list);
        for (Body b : list) {
            b.vx += 0.5 * (b.prevAx + b.ax) * h;
            b.vy += 0.5 * (b.prevAy + b.ay) * h;
        }
    }

    /** Advance the whole universe by one time step. */
    public void step() {
        if (useVerlet) {
            if (!accelValid) {
                computeAccelerations(bodies);
                accelValid = true;
            }
            verletStep(bodies, dt);
        } else {
            eulerStep(bodies, dt);
            accelValid = false;
        }
        time += dt;
    }

    // ------------------------------------------------------------------
    // collisions and escapes
    // ------------------------------------------------------------------

    /** Returns {i, j} for two touching bodies, or null if nothing is touching. */
    private int[] findCollision() {
        for (int i = 0; i < bodies.size(); i++) {
            for (int j = i + 1; j < bodies.size(); j++) {
                Body a = bodies.get(i);
                Body b = bodies.get(j);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double touch = a.radius + b.radius;
                if (dx * dx + dy * dy < touch * touch) {
                    return new int[] { i, j };
                }
            }
        }
        return null;
    }

    /** Merge touching bodies into one, conserving momentum (a perfectly sticky collision). */
    public void mergeCollisions() {
        int[] pair = findCollision();
        while (pair != null) {
            Body a = bodies.get(pair[0]);
            Body b = bodies.get(pair[1]);
            Body big = (a.mass >= b.mass) ? a : b;
            Body small = (big == a) ? b : a;

            double total = a.mass + b.mass;
            big.x = (a.x * a.mass + b.x * b.mass) / total;
            big.y = (a.y * a.mass + b.y * b.mass) / total;
            big.vx = (a.vx * a.mass + b.vx * b.mass) / total;
            big.vy = (a.vy * a.mass + b.vy * b.mass) / total;
            big.mass = total;
            big.radius = Body.radiusFor(total);

            bodies.remove(small);
            merged++;
            accelValid = false;
            baselineValid = false;
            pair = findCollision();
        }
    }

    /** Delete bodies that have been flung very far away. */
    public void removeEscaped(double cx, double cy, double limit) {
        for (int i = bodies.size() - 1; i >= 0; i--) {
            Body b = bodies.get(i);
            double dx = b.x - cx;
            double dy = b.y - cy;
            if (Math.sqrt(dx * dx + dy * dy) > limit) {
                bodies.remove(i);
                escaped++;
                accelValid = false;
                baselineValid = false;
            }
        }
    }

    // ------------------------------------------------------------------
    // energy (a great way to see whether your integrator is any good)
    // ------------------------------------------------------------------
    public double totalEnergy() {
        double kinetic = 0;
        double potential = 0;
        double eps2 = softening * softening;
        for (int i = 0; i < bodies.size(); i++) {
            Body a = bodies.get(i);
            kinetic += a.kineticEnergy();
            for (int j = i + 1; j < bodies.size(); j++) {
                Body b = bodies.get(j);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                potential -= G * a.mass * b.mass / Math.sqrt(dx * dx + dy * dy + eps2);
            }
        }
        return kinetic + potential;
    }

    /** How far has total energy drifted from where it started (in percent)? NaN if < 2 bodies. */
    public double energyDriftPercent() {
        if (bodies.size() < 2) {
            return Double.NaN;
        }
        if (!baselineValid) {
            baselineEnergy = totalEnergy();
            baselineValid = true;
        }
        return (totalEnergy() - baselineEnergy) / Math.abs(baselineEnergy) * 100.0;
    }

    // ------------------------------------------------------------------
    // trajectory preview while you are dragging
    // ------------------------------------------------------------------

    /** Simulate a massless "probe" forward in time on a COPY of the universe. */
    public ArrayList<double[]> predictPath(double x, double y, double vx, double vy) {
        ArrayList<double[]> path = new ArrayList<double[]>();
        if (bodies.size() > 60) {
            return path; // too expensive to preview
        }
        ArrayList<Body> copy = new ArrayList<Body>();
        for (Body b : bodies) {
            copy.add(new Body(b.x, b.y, b.vx, b.vy, b.mass, b.color));
        }
        Body probe = new Body(x, y, vx, vy, 0, Color.WHITE);
        copy.add(probe);
        computeAccelerations(copy);
        for (int i = 0; i < 500; i++) {
            verletStep(copy, 0.5);
            if (i % 3 == 0) {
                path.add(new double[] { probe.x, probe.y });
            }
        }
        return path;
    }

    // ------------------------------------------------------------------
    // presets
    // ------------------------------------------------------------------

    private Color planetColor() {
        return Color.getHSBColor(rand.nextFloat(), 0.55f, 1.0f);
    }

    public static Color starColor() {
        return new Color(255, 210, 90);
    }

    /** Remove net drift so the whole system doesn't slide off the screen. */
    private void zeroMomentum() {
        double px = 0, py = 0, m = 0;
        for (Body b : bodies) {
            px += b.vx * b.mass;
            py += b.vy * b.mass;
            m += b.mass;
        }
        if (m == 0) {
            return;
        }
        for (Body b : bodies) {
            b.vx -= px / m;
            b.vy -= py / m;
        }
    }

    public void loadSolarSystem(double cx, double cy) {
        clear();
        double starMass = 3000;
        addBody(cx, cy, 0, 0, starMass, starColor());
        double[] radii = { 90, 150, 220, 300, 380 };
        double[] masses = { 5, 12, 8, 20, 3 };
        for (int i = 0; i < radii.length; i++) {
            double r = radii[i];
            double angle = rand.nextDouble() * 2 * Math.PI;
            double speed = Math.sqrt(G * starMass / r); // circular orbit speed: v = sqrt(GM/r)
            addBody(cx + r * Math.cos(angle), cy + r * Math.sin(angle),
                    -Math.sin(angle) * speed, Math.cos(angle) * speed,
                    masses[i], planetColor());
        }
        zeroMomentum();
    }

    public void loadBinaryStar(double cx, double cy) {
        clear();
        double m = 1500;
        double d = 180; // separation
        double v = Math.sqrt(G * m / (2 * d));
        addBody(cx - d / 2, cy, 0, -v, m, starColor());
        addBody(cx + d / 2, cy, 0, v, m, starColor());
        double r = 300;
        double pv = Math.sqrt(G * 2 * m / r);
        addBody(cx + r, cy, 0, pv, 6, planetColor());
        zeroMomentum();
    }

    /** The famous figure-eight three-body orbit (Chenciner & Montgomery, 2000). */
    public void loadFigureEight(double cx, double cy) {
        clear();
        double m = 1000;
        double length = 150;
        double vs = Math.sqrt(G * m / length);
        double px = 0.97000436, py = -0.24308753;
        double vx3 = -0.93240737, vy3 = -0.86473146;
        addBody(cx + px * length, cy + py * length, -vx3 / 2 * vs, -vy3 / 2 * vs, m, new Color(255, 120, 120));
        addBody(cx - px * length, cy - py * length, -vx3 / 2 * vs, -vy3 / 2 * vs, m, new Color(120, 255, 160));
        addBody(cx, cy, vx3 * vs, vy3 * vs, m, new Color(120, 170, 255));
    }

    public void loadChaos(double cx, double cy) {
        clear();
        for (int i = 0; i < 40; i++) {
            double angle = rand.nextDouble() * 2 * Math.PI;
            double dist = Math.sqrt(rand.nextDouble()) * 250;
            double mass = 5 + rand.nextDouble() * 25;
            addBody(cx + dist * Math.cos(angle), cy + dist * Math.sin(angle),
                    (rand.nextDouble() - 0.5) * 3, (rand.nextDouble() - 0.5) * 3,
                    mass, planetColor());
        }
        zeroMomentum();
    }
}
