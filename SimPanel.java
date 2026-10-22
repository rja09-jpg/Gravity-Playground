import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The drawing surface. Handles the mouse, runs the animation loop,
 * and paints everything. The physics itself lives in Simulation.
 */
public class SimPanel extends JPanel {
    private static final double LAUNCH_SCALE = 0.05;  // drag pixels -> velocity
    private static final double VELOCITY_ARROW = 10;  // arrow length per unit of velocity
    private static final double ACCEL_ARROW = 400;    // arrow length per unit of acceleration

    private Simulation sim = new Simulation();
    private Timer timer;
    private java.util.Random rand = new java.util.Random();

    private boolean paused = false;
    private boolean showVelocity = true;
    private boolean showAccel = false;
    private boolean showTrails = true;
    private int stepsPerFrame = 4;
    private double newMass = 10;

    private boolean dragging = false;
    private int startX, startY, curX, curY;

    public SimPanel() {
        setBackground(new Color(4, 5, 11));
        setPreferredSize(new Dimension(900, 700));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragging = true;
                startX = e.getX();
                startY = e.getY();
                curX = startX;
                curY = startY;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                curX = e.getX();
                curY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!dragging) {
                    return;
                }
                dragging = false;
                double vx = (e.getX() - startX) * LAUNCH_SCALE;
                double vy = (e.getY() - startY) * LAUNCH_SCALE;
                Color c = (newMass >= 1000) ? Simulation.starColor()
                        : Color.getHSBColor(rand.nextFloat(), 0.55f, 1.0f);
                sim.addBody(startX, startY, vx, vy, newMass, c);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        // ~60 frames per second
        timer = new Timer(16, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tick();
                repaint();
            }
        });
        timer.start();
    }

    // ------------------------------------------------------------------
    // controls used by the side panel
    // ------------------------------------------------------------------
    public Simulation getSimulation() { return sim; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public void setShowVelocity(boolean b) { showVelocity = b; }
    public void setShowAccel(boolean b) { showAccel = b; }
    public void setShowTrails(boolean b) { showTrails = b; }
    public void setStepsPerFrame(int n) { stepsPerFrame = n; }
    public void setNewMass(double m) { newMass = m; }

    public void loadPreset(String name) {
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0;
        if (name.equals("Solar system")) {
            sim.loadSolarSystem(cx, cy);
        } else if (name.equals("Binary star")) {
            sim.loadBinaryStar(cx, cy);
        } else if (name.equals("Figure-eight")) {
            sim.loadFigureEight(cx, cy);
        } else if (name.equals("Chaos cluster")) {
            sim.loadChaos(cx, cy);
        } else {
            sim.clear();
        }
    }

    // ------------------------------------------------------------------
    // one animation frame
    // ------------------------------------------------------------------
    private void tick() {
        if (paused) {
            return;
        }
        for (int i = 0; i < stepsPerFrame; i++) {
            sim.step();
            if (sim.isMergeEnabled()) {
                sim.mergeCollisions();
            }
        }
        sim.removeEscaped(getWidth() / 2.0, getHeight() / 2.0,
                2.5 * Math.max(getWidth(), getHeight()));
        for (Body b : sim.getBodies()) {
            b.recordTrail();
        }
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        ArrayList<Body> bodies = sim.getBodies();

        if (showTrails) {
            drawTrails(g, bodies);
        }
        for (Body b : bodies) {
            drawBody(g, b);
        }
        if (dragging) {
            drawLaunchPreview(g);
        }
        drawHud(g, bodies);
    }

    private void drawTrails(Graphics2D g, ArrayList<Body> bodies) {
        g.setStroke(new BasicStroke(1.2f));
        for (Body b : bodies) {
            int n = b.trail.size();
            for (int i = 1; i < n; i++) {
                double[] p = b.trail.get(i - 1);
                double[] q = b.trail.get(i);
                int alpha = (int) (15 + 130.0 * i / n); // older = fainter
                g.setColor(new Color(b.color.getRed(), b.color.getGreen(), b.color.getBlue(), alpha));
                g.drawLine((int) p[0], (int) p[1], (int) q[0], (int) q[1]);
            }
        }
    }

    private void drawBody(Graphics2D g, Body b) {
        if (b.mass >= 300) { // massive things get a glow
            g.setColor(new Color(b.color.getRed(), b.color.getGreen(), b.color.getBlue(), 40));
            double glow = b.radius * 2.2;
            g.fill(new Ellipse2D.Double(b.x - glow, b.y - glow, glow * 2, glow * 2));
        }
        g.setColor(b.color);
        g.fill(new Ellipse2D.Double(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2));

        if (showVelocity) {
            drawArrow(g, b.x, b.y, b.vx * VELOCITY_ARROW, b.vy * VELOCITY_ARROW, new Color(90, 255, 140));
        }
        if (showAccel) {
            drawArrow(g, b.x, b.y, b.ax * ACCEL_ARROW, b.ay * ACCEL_ARROW, new Color(255, 100, 100));
        }
    }

    private void drawArrow(Graphics2D g, double x, double y, double dx, double dy, Color c) {
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 2) {
            return;
        }
        if (len > 120) { // keep huge arrows on screen
            dx = dx / len * 120;
            dy = dy / len * 120;
            len = 120;
        }
        double ex = x + dx;
        double ey = y + dy;
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine((int) x, (int) y, (int) ex, (int) ey);
        double angle = Math.atan2(dy, dx);
        double head = Math.min(8, len / 2);
        g.drawLine((int) ex, (int) ey,
                (int) (ex - head * Math.cos(angle - 0.4)), (int) (ey - head * Math.sin(angle - 0.4)));
        g.drawLine((int) ex, (int) ey,
                (int) (ex - head * Math.cos(angle + 0.4)), (int) (ey - head * Math.sin(angle + 0.4)));
    }

    private void drawLaunchPreview(Graphics2D g) {
        double vx = (curX - startX) * LAUNCH_SCALE;
        double vy = (curY - startY) * LAUNCH_SCALE;

        // the planet you are about to drop
        double r = Body.radiusFor(newMass);
        g.setColor(new Color(255, 255, 255, 160));
        g.fill(new Ellipse2D.Double(startX - r, startY - r, r * 2, r * 2));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(startX, startY, curX, curY);

        // dotted line = where it WILL go (assuming nothing else changes)
        ArrayList<double[]> path = sim.predictPath(startX, startY, vx, vy);
        g.setColor(new Color(255, 255, 255, 110));
        for (double[] p : path) {
            g.fill(new Ellipse2D.Double(p[0] - 1, p[1] - 1, 2, 2));
        }
    }

    private void drawHud(Graphics2D g, ArrayList<Body> bodies) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.setColor(new Color(200, 210, 230));
        int x = getWidth() - 220;
        int y = 22;
        g.drawString("bodies:  " + bodies.size(), x, y);
        g.drawString("time:    " + String.format("%.0f", sim.getTime()), x, y + 18);
        g.drawString("escaped: " + sim.getEscaped(), x, y + 36);
        g.drawString("merged:  " + sim.getMerged(), x, y + 54);
        double drift = sim.energyDriftPercent();
        String driftText = Double.isNaN(drift) ? "--" : String.format("%+.2f%%", drift);
        g.drawString("E drift: " + driftText, x, y + 72);

        g.setColor(new Color(150, 160, 185));
        g.drawString("click = drop a body   drag = give it velocity (dotted line shows the path)",
                14, getHeight() - 14);
        if (paused) {
            g.setColor(new Color(255, 200, 80));
            g.drawString("PAUSED", 14, 22);
        }
    }
}
